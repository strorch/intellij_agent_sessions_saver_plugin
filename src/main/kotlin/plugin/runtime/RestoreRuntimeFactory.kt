package plugin.runtime

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import plugin.adapters.AgentAdapterRegistry
import plugin.adapters.AgentResumeStatus
import plugin.adapters.providers.ClaudeAgentAdapter
import plugin.adapters.providers.CopilotAgentAdapter
import plugin.adapters.providers.CodexAgentAdapter
import plugin.adapters.providers.OpenCodeAgentAdapter
import plugin.persistence.SnapshotMigrationService
import plugin.persistence.SnapshotSerializer
import plugin.persistence.TerminalSessionSnapshotStore
import plugin.persistence.model.TerminalSessionSnapshot
import plugin.restore.ProjectScopeGuard
import plugin.restore.RestoreAttemptRunner
import plugin.restore.RestoreCoordinator
import plugin.restore.RetryPolicyEngine
import plugin.restore.ui.RestoreSummaryCounters
import plugin.restore.ui.RestoreSummaryNotifier
import plugin.restore.ui.StartupSessionChooser
import plugin.restore.ui.StartupSessionChooserRequest
import plugin.settings.RestorePolicySettingsService
import plugin.telemetry.RestoreTelemetryLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class RestoreDispatchSummary(
    val attempted: Int,
    val restored: Int,
    val exactRestored: Int,
    val chooserResolved: Int,
    val fallbackRestored: Int,
    val skipped: Int,
)

private enum class RestoreMode {
    EXACT,
    CHOOSER,
    FALLBACK,
}

data class RestoreRuntime(
    val project: Project,
    val projectScopeId: String,
    val snapshotStore: TerminalSessionSnapshotStore,
    val settingsService: RestorePolicySettingsService,
    val projectScopeGuard: ProjectScopeGuard,
    val restoreCoordinator: RestoreCoordinator,
    val summaryNotifier: RestoreSummaryNotifier,
    val startupChooser: StartupSessionChooser,
) {
    private val numericVersionPattern = Regex("^[.-]?\\d+(?:\\.\\d+){1,4}$")
    private val uuidPattern = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val ambiguityClassifier = StartupAmbiguityClassifier()

    fun startupRestore(): RestoreDispatchSummary {
        if (!settingsService.isStartupRestoreEnabled()) {
            val emptySummary = RestoreDispatchSummary(0, 0, 0, 0, 0, 0)
            summaryNotifier.publish(emptyList(), emptySummary.toCounters())
            return emptySummary
        }
        val snapshots = snapshotStore.load(projectScopeId, currentSchemaVersion = 3)
        val scoped = projectScopeGuard.filterByProject(snapshots, projectScopeId)
        return dispatchRestore(scoped, timeoutSeconds = 30, useStartupChooser = true)
    }

    fun manualRestore(): Int {
        val snapshots = snapshotStore.load(projectScopeId, currentSchemaVersion = 3)
        val scoped = projectScopeGuard.filterByProject(snapshots, projectScopeId)
        return dispatchRestore(scoped, timeoutSeconds = 30, useStartupChooser = false).restored
    }

    fun retryFailed(): Int {
        val failedTabs = summaryNotifier.latest()
            .filter { it.status != AgentResumeStatus.SUCCESS.name }
            .map { it.terminalTabId }
            .toSet()
        if (failedTabs.isEmpty()) {
            return 0
        }
        val snapshots = snapshotStore.load(projectScopeId, currentSchemaVersion = 3)
        val retryCandidates = snapshots.filter { it.terminalTabId in failedTabs }
        return dispatchRestore(retryCandidates, timeoutSeconds = 30, useStartupChooser = false).restored
    }

    private fun dispatchRestore(
        candidates: List<TerminalSessionSnapshot>,
        timeoutSeconds: Int,
        useStartupChooser: Boolean,
    ): RestoreDispatchSummary {
        val normalized = candidates.map { normalizeSessionReference(it) }
        val modeByTab = mutableMapOf<String, RestoreMode>()
        val dispatchQueue = mutableListOf<TerminalSessionSnapshot>()
        var skipped = 0
        var chooserResolved = 0

        val buckets = ambiguityClassifier.partition(
            normalized,
            { isExplicitSessionReference(it.sessionReference, it.agentType, it.sessionReferenceSource) },
            useStartupChooser,
        )
        val exact = buckets.exact
        val fallback = buckets.fallback
        val ambiguous = buckets.ambiguous
        exact.forEach { modeByTab[it.terminalTabId] = RestoreMode.EXACT }
        fallback.forEach { modeByTab[it.terminalTabId] = RestoreMode.FALLBACK }

        exact.forEach { snapshot ->
            if (isRestorableSnapshot(snapshot)) {
                dispatchQueue.add(snapshot)
            } else {
                skipped += 1
            }
        }
        fallback.forEach { snapshot ->
            if (isRestorableSnapshot(snapshot)) {
                dispatchQueue.add(snapshot)
            } else {
                skipped += 1
            }
        }

        val chooserResolvedSnapshots = resolveAmbiguousSnapshots(ambiguous)
        chooserResolved = chooserResolvedSnapshots.count { !it.sessionReference.isNullOrBlank() }
        chooserResolvedSnapshots.forEach { snapshot ->
            val mode = if (snapshot.sessionReference.isNullOrBlank()) RestoreMode.FALLBACK else RestoreMode.CHOOSER
            modeByTab[snapshot.terminalTabId] = mode
            if (isRestorableSnapshot(snapshot)) {
                dispatchQueue.add(snapshot)
            } else {
                skipped += 1
            }
        }

        if (dispatchQueue.isEmpty()) {
            val emptySummary = RestoreDispatchSummary(0, 0, 0, chooserResolved, 0, skipped)
            summaryNotifier.publish(emptyList(), emptySummary.toCounters())
            return emptySummary
        }
        val commandExecutors = resolveTerminalCommandExecutors(dispatchQueue)
        val results = restoreCoordinator.restore(
            projectScopeId,
            dispatchQueue,
            timeoutSeconds = timeoutSeconds,
            terminalCommandExecutors = commandExecutors,
        )
        val finalByTab = results.groupBy { it.terminalTabId }.mapValues { entry -> entry.value.last() }
        val restoredTabs = finalByTab.filterValues { it.status == AgentResumeStatus.SUCCESS.name }.keys
        val exactRestored = restoredTabs.count { modeByTab[it] == RestoreMode.EXACT }
        val fallbackRestored = restoredTabs.count { modeByTab[it] == RestoreMode.FALLBACK }
        val summary = RestoreDispatchSummary(
            attempted = dispatchQueue.size,
            restored = restoredTabs.size,
            exactRestored = exactRestored,
            chooserResolved = chooserResolved,
            fallbackRestored = fallbackRestored,
            skipped = skipped,
        )
        summaryNotifier.publish(results, summary.toCounters())
        return summary
    }

    private fun resolveAmbiguousSnapshots(
        ambiguous: List<TerminalSessionSnapshot>,
    ): List<TerminalSessionSnapshot> {
        if (ambiguous.isEmpty()) {
            return emptyList()
        }
        val requests = ambiguous.map { snapshot ->
            StartupSessionChooserRequest(
                terminalTabId = snapshot.terminalTabId,
                terminalDisplayName = snapshot.terminalDisplayName,
                candidates = snapshot.sessionCandidates.distinct(),
            )
        }
        val choices = startupChooser.choose(project, requests)
        return ambiguous.map { snapshot ->
            val selection = choices[snapshot.terminalTabId]
            val selectedReference = selection?.selectedSessionReference?.trim().orEmpty()
            if (selectedReference.isNotBlank()) {
                snapshot.copy(
                    sessionReference = selectedReference,
                    sessionReferenceSource = "CHOOSER",
                    sessionReferenceConfidence = "HIGH",
                )
            } else {
                snapshot.copy(sessionReference = null)
            }
        }
    }

    private fun isRestorableSnapshot(snapshot: TerminalSessionSnapshot): Boolean {
        if (!snapshot.hadActiveAgentSession) {
            return false
        }
        if (
            !supportsImplicitResume(snapshot.agentType) &&
            !isExplicitSessionReference(snapshot.sessionReference, snapshot.agentType, snapshot.sessionReferenceSource)
        ) {
            return false
        }
        return snapshot.isValid(allowFallback = supportsImplicitResume(snapshot.agentType))
    }

    private fun normalizeSessionReference(snapshot: TerminalSessionSnapshot): TerminalSessionSnapshot {
        val normalizedReference = sanitizeSessionReference(
            snapshot.sessionReference,
            snapshot.agentType,
            snapshot.sessionReferenceSource,
        )
        val normalizedCandidates = snapshot.sessionCandidates
            .mapNotNull { sanitizeSessionReference(it, snapshot.agentType, snapshot.sessionReferenceSource) }
            .distinct()
            .take(6)
        return snapshot.copy(sessionReference = normalizedReference, sessionCandidates = normalizedCandidates)
    }

    private fun isExplicitSessionReference(sessionReference: String?, agentTypeRaw: String, sourceRaw: String?): Boolean {
        val sanitized = sanitizeSessionReference(sessionReference, agentTypeRaw, sourceRaw)
        if (sanitized.isNullOrBlank()) {
            return false
        }
        return true
    }

    private fun sanitizeSessionReference(sessionReference: String?, agentTypeRaw: String, sourceRaw: String?): String? {
        if (sessionReference.isNullOrBlank()) {
            return null
        }
        val normalized = sessionReference.trim().trim('"', '\'')
        if (normalized.isBlank() || normalized.startsWith("detected-")) {
            return null
        }
        if (isUnsafeUuid(agentTypeRaw, sourceRaw, normalized)) {
            return null
        }
        if (numericVersionPattern.matches(normalized)) {
            return null
        }
        return normalized
    }

    private fun isUnsafeUuid(agentTypeRaw: String, sourceRaw: String?, value: String): Boolean {
        if (agentTypeRaw.lowercase() == "copilot") {
            return false
        }
        if (!uuidPattern.matches(value)) {
            return false
        }
        return when (sourceRaw?.uppercase()) {
            "COMMAND_LINE", "PROCESS_ENV", "PROVIDER_STATE", "CHOOSER" -> false
            else -> true
        }
    }

    private fun supportsImplicitResume(agentTypeRaw: String): Boolean {
        return when (agentTypeRaw.lowercase()) {
            "codex", "claude", "opencode", "copilot" -> true
            else -> false
        }
    }

    private fun resolveTerminalCommandExecutors(
        snapshots: List<TerminalSessionSnapshot>,
    ): Map<String, (String) -> Boolean> {
        val manager = TerminalToolWindowManager.getInstance(project)
        val available = manager.terminalWidgets.mapNotNull { widget ->
            val shellWidget = ShellTerminalWidget.asShellJediTermWidget(widget) ?: return@mapNotNull null
            val title = shellWidget.terminalTitle.buildTitle().ifBlank { "Terminal" }
            DispatchTarget(title) { command ->
                runCatching {
                    shellWidget.asNewWidget().sendCommandToExecute(command)
                }.isSuccess
            }
        }.toMutableList()
        val executors = mutableMapOf<String, (String) -> Boolean>()
        snapshots.forEach { snapshot ->
            val indexByTitle = available.indexOfFirst { it.title == snapshot.terminalDisplayName }
            val selectedIndex = if (indexByTitle >= 0) indexByTitle else 0
            if (available.isNotEmpty()) {
                val target = available.removeAt(selectedIndex)
                executors[snapshot.terminalTabId] = target.executor
            } else {
                val workingDirectory = project.basePath ?: FileUtil.getTempDirectory()
                val shellWidget = manager.createLocalShellWidget(workingDirectory, snapshot.terminalDisplayName)
                executors[snapshot.terminalTabId] = { command ->
                    runCatching {
                        shellWidget.asNewWidget().sendCommandToExecute(command)
                    }.isSuccess
                }
            }
        }
        return executors
    }
}

private data class DispatchTarget(
    val title: String,
    val executor: (String) -> Boolean,
)

private fun RestoreDispatchSummary.toCounters(): RestoreSummaryCounters {
    return RestoreSummaryCounters(
        attempted = attempted,
        exactRestored = exactRestored,
        chooserResolved = chooserResolved,
        fallbackRestored = fallbackRestored,
        skipped = skipped,
    )
}

object RestoreRuntimeFactory {
    private val runtimes = ConcurrentHashMap<String, RestoreRuntime>()

    fun forProject(project: Project): RestoreRuntime {
        val scopeId = project.locationHash
        return runtimes.computeIfAbsent(scopeId) {
            val storageDir = resolveStorageDir(project)
            val snapshotStore = TerminalSessionSnapshotStore(
                serializer = SnapshotSerializer(),
                migrationService = SnapshotMigrationService(),
                baseDir = storageDir,
            )
            val settingsService = RestorePolicySettingsService()
            val summaryNotifier = RestoreSummaryNotifier()
            val telemetry = RestoreTelemetryLogger()
            val registry = AgentAdapterRegistry(
                listOf(CodexAgentAdapter(), ClaudeAgentAdapter(), OpenCodeAgentAdapter(), CopilotAgentAdapter()),
            )
            val coordinator = RestoreCoordinator(
                adapterRegistry = registry,
                attemptRunner = RestoreAttemptRunner(),
                retryPolicyEngine = RetryPolicyEngine(),
                notifier = summaryNotifier,
                telemetryLogger = telemetry,
            )
            RestoreRuntime(
                project = project,
                projectScopeId = scopeId,
                snapshotStore = snapshotStore,
                settingsService = settingsService,
                projectScopeGuard = ProjectScopeGuard(),
                restoreCoordinator = coordinator,
                summaryNotifier = summaryNotifier,
                startupChooser = StartupSessionChooser(),
            )
        }
    }

    private fun resolveStorageDir(project: Project): File {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            val tempDir = File(FileUtil.getTempDirectory(), "agent-session-saver")
            tempDir.mkdirs()
            return tempDir
        }
        val projectDir = File(basePath)
        val targetDir = File(File(projectDir, ".idea"), "agent-session-saver")
        migrateLegacyStorage(projectDir, targetDir)
        targetDir.mkdirs()
        return targetDir
    }

    private fun migrateLegacyStorage(projectDir: File, targetDir: File) {
        val legacyDir = File(projectDir, ".agent-session-saver")
        if (!legacyDir.exists() || !legacyDir.isDirectory) {
            return
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val copiedAll = legacyDir.listFiles().orEmpty().all { source ->
            val target = File(targetDir, source.name)
            if (target.exists()) {
                true
            } else if (source.isDirectory) {
                source.copyRecursively(target, overwrite = false)
            } else {
                source.copyTo(target, overwrite = false)
                true
            }
        }
        if (copiedAll) {
            legacyDir.deleteRecursively()
        }
    }

    fun evict(project: Project) {
        runtimes.remove(project.locationHash)
    }

    fun notify(project: Project, title: String, content: String, type: NotificationType) {
        val app = ApplicationManager.getApplication()
        if (app.isHeadlessEnvironment || app.isUnitTestMode || project.isDisposed) {
            return
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Agent Sessions Saver")
            .createNotification(title, content, type)
            .notify(project)
    }
}
