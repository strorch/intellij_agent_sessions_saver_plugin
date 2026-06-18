package plugin.capture

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import plugin.adapters.TerminalTabMapper
import plugin.adapters.TerminalTabState
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverChain
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.ResolvedSessionReference
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceSource
import plugin.persistence.SessionSnapshotCaptureService
import plugin.persistence.TerminalSessionSnapshotStore
import plugin.persistence.model.TerminalSessionSnapshot
import plugin.runtime.RestoreRuntimeFactory
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class CaptureSummary(
    val totalTabs: Int,
    val capturedSnapshots: Int,
    val activeSessions: Int,
)

/**
 * Raw, cheaply-readable terminal state captured synchronously while the project/terminals are
 * still alive. Holds only plain strings/numbers so that the expensive resolution (subprocess
 * execution, /proc reads via the resolver chain) and persistence (file IO) can be deferred to a
 * background thread without touching potentially-disposed UI components.
 */
internal data class RawTabCapture(
    val index: Int,
    val title: String,
    val typedCommand: String,
    val shellCommand: String,
    val terminalText: String,
    val processMetadata: ProcessMetadata,
)

object TerminalSnapshotCapture {
    private val resolverChain = ResolverChain()
    private val log = logger<TerminalSnapshotCapture>()

    /**
     * Upper bound on how long [captureOnClose] will wait on the EDT for the background resolution
     * to finish before falling back to persisting a best-effort (un-resolved) snapshot. Kept small
     * so a hung subprocess (`opencode session list`) can never stall project close indefinitely,
     * but generous enough that resolution normally completes and the fully-resolved snapshot is
     * written.
     */
    private const val CLOSE_RESOLUTION_TIMEOUT_MS = 1500L

    /**
     * Synchronous capture used by the manual "Capture Now" action. Reads widget state and resolves
     * references in the calling context and returns a summary. Prefer [captureOnClose] for the
     * project-closing path so that the blocking resolution does not stall the EDT.
     */
    fun capture(project: Project): CaptureSummary {
        val runtime = RestoreRuntimeFactory.forProject(project)
        val scopeId = runtime.projectScopeId
        val rawTabs = readRawTabs(project)
        val tabs = resolveTabs(scopeId, project.basePath, rawTabs)
        val snapshots = persist(runtime.snapshotStore, scopeId, tabs)
        return CaptureSummary(
            totalTabs = rawTabs.size,
            capturedSnapshots = snapshots.size,
            activeSessions = snapshots.count { it.hadActiveAgentSession },
        )
    }

    /**
     * Project-close capture. Threading/persistence model:
     *
     *  1. Cheap, EDT-bound widget reads (terminal buffer text, titles, reflective process
     *     metadata) happen synchronously, because the terminals are disposed once close proceeds.
     *  2. The genuinely slow resolution — the resolver chain, which may spawn `opencode session
     *     list` and read /proc — runs on a pooled background thread so it never executes on the
     *     EDT.
     *  3. The EDT then waits (bounded by [CLOSE_RESOLUTION_TIMEOUT_MS]) for that resolution and
     *     persists the snapshot synchronously *before close returns*. Serializing and writing a
     *     small JSON file is fast and safe to do on the closing path, and doing it synchronously
     *     restores the guarantee that the last snapshot is actually written — unlike the previous
     *     fire-and-forget approach, where a pooled thread could be killed on IDE shutdown before
     *     the write completed.
     *  4. If resolution does not finish within the timeout (e.g. a hung subprocess), we still
     *     persist a best-effort snapshot built from the already-captured raw state without the
     *     resolved references, so the snapshot is never lost.
     *
     * The [resolutionExecutor] and [resolutionTimeoutMs] are injectable so the persistence
     * guarantee can be exercised deterministically in tests.
     */
    fun captureOnClose(
        project: Project,
        resolutionExecutor: (Callable<List<TerminalTabState>>) -> Future<List<TerminalTabState>> = {
            ApplicationManager.getApplication().executeOnPooledThread(it)
        },
        resolutionTimeoutMs: Long = CLOSE_RESOLUTION_TIMEOUT_MS,
    ) {
        val runtime = RestoreRuntimeFactory.forProject(project)
        val scopeId = runtime.projectScopeId
        val projectBasePath = project.basePath
        val rawTabs = readRawTabs(project)
        awaitAndPersist(runtime.snapshotStore, scopeId, projectBasePath, rawTabs, resolutionExecutor, resolutionTimeoutMs)
    }

    /**
     * Core close-path persistence guarantee, decoupled from [Project]/widget access so it can be
     * exercised deterministically in tests. Runs resolution on the supplied executor, waits for it
     * (bounded), then persists synchronously — falling back to an un-resolved snapshot on timeout.
     */
    internal fun awaitAndPersist(
        snapshotStore: TerminalSessionSnapshotStore,
        scopeId: String,
        projectBasePath: String?,
        rawTabs: List<RawTabCapture>,
        resolutionExecutor: (Callable<List<TerminalTabState>>) -> Future<List<TerminalTabState>>,
        resolutionTimeoutMs: Long,
    ): List<TerminalSessionSnapshot> {
        val resolutionFuture = resolutionExecutor(
            Callable { resolveTabs(scopeId, projectBasePath, rawTabs) },
        )
        val tabs = awaitResolvedTabs(resolutionFuture, scopeId, projectBasePath, rawTabs, resolutionTimeoutMs)
        return persist(snapshotStore, scopeId, tabs)
    }

    /** Visible for tests: builds the raw capture record without any widget access. */
    internal fun rawTabForTest(
        index: Int,
        title: String,
        typedCommand: String = "",
        shellCommand: String = "",
        terminalText: String = "",
        processMetadata: ProcessMetadata = ProcessMetadata(null, ""),
    ): RawTabCapture = RawTabCapture(index, title, typedCommand, shellCommand, terminalText, processMetadata)

    private fun awaitResolvedTabs(
        future: Future<List<TerminalTabState>>,
        scopeId: String,
        projectBasePath: String?,
        rawTabs: List<RawTabCapture>,
        timeoutMs: Long,
    ): List<TerminalTabState> {
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            // Resolution is still running (likely a slow/hung subprocess). Do not block close any
            // longer; persist a best-effort snapshot so the close-time state is never lost.
            future.cancel(true)
            log.warn("Session resolution timed out on project close; persisting un-resolved snapshot")
            rawTabs.map { unresolvedTabState(scopeId, projectBasePath, it) }
        } catch (ex: Exception) {
            future.cancel(true)
            log.warn("Session resolution failed on project close; persisting un-resolved snapshot", ex)
            rawTabs.map { unresolvedTabState(scopeId, projectBasePath, it) }
        }
    }

    private fun readRawTabs(project: Project): List<RawTabCapture> {
        val widgets = TerminalToolWindowManager.getInstance(project).terminalWidgets.toList()
        return widgets.mapIndexed { index, widget ->
            readRawTab(index, ShellTerminalWidget.asShellJediTermWidget(widget))
        }
    }

    private fun resolveTabs(
        scopeId: String,
        projectBasePath: String?,
        rawTabs: List<RawTabCapture>,
    ): List<TerminalTabState> {
        return rawTabs.map { toTabState(scopeId, projectBasePath, it) }
    }

    private fun persist(
        snapshotStore: TerminalSessionSnapshotStore,
        scopeId: String,
        tabs: List<TerminalTabState>,
    ): List<TerminalSessionSnapshot> {
        val captureService = SessionSnapshotCaptureService(TerminalTabMapper())
        val snapshots = captureService.capture(scopeId, tabs)
        snapshotStore.save(scopeId, schemaVersion = 3, snapshots = snapshots)
        return snapshots
    }

    /**
     * Builds a tab state from raw capture without running the resolver chain. Used as the
     * best-effort fallback when close-time resolution does not complete in time. Detection (which
     * is cheap and in-process) still runs so an active agent session is still recorded.
     */
    private fun unresolvedTabState(
        scopeId: String,
        projectBasePath: String?,
        raw: RawTabCapture,
    ): TerminalTabState {
        val title = raw.title
        val tabId = "terminal-${raw.index + 1}-${title.hashCode().toUInt().toString(16)}"
        val processMetadata = raw.processMetadata
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = processMetadata.processCommand,
                typedCommand = raw.typedCommand,
                shellCommand = raw.shellCommand,
                terminalText = raw.terminalText,
            ),
        )
        return TerminalTabState(
            projectScopeId = scopeId,
            terminalTabId = tabId,
            terminalDisplayName = title,
            agentType = detection.agentType,
            sessionReference = null,
            sessionReferenceSource = null,
            sessionReferenceConfidence = null,
            sessionCandidates = emptyList(),
            processId = processMetadata.processId,
            processCommand = processMetadata.processCommand,
            hasActiveAgentSession = detection.hasActiveSession,
        )
    }

    private fun readRawTab(index: Int, widget: ShellTerminalWidget?): RawTabCapture {
        val title = widget?.terminalTitle?.buildTitle()
            ?.takeIf { it.isNotBlank() }
            ?: "Terminal ${index + 1}"
        return RawTabCapture(
            index = index,
            title = title,
            typedCommand = widget?.typedShellCommand.orEmpty(),
            shellCommand = widget?.shellCommand?.joinToString(" ").orEmpty(),
            terminalText = widget?.text.orEmpty(),
            processMetadata = readProcessMetadata(widget),
        )
    }

    private fun toTabState(
        scopeId: String,
        projectBasePath: String?,
        raw: RawTabCapture,
    ): TerminalTabState {
        val title = raw.title
        val tabId = "terminal-${raw.index + 1}-${title.hashCode().toUInt().toString(16)}"
        val processMetadata = raw.processMetadata
        val processCommandLine = processMetadata.processCommand
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = processCommandLine,
                typedCommand = raw.typedCommand,
                shellCommand = raw.shellCommand,
                terminalText = raw.terminalText,
            ),
        )
        val resolved = resolverChain.resolve(
            ResolverContext(
                agentType = detection.agentType,
                projectBasePath = projectBasePath,
                processMetadata = processMetadata,
                processCommandLine = processCommandLine,
                typedCommand = raw.typedCommand,
                shellCommand = raw.shellCommand,
                terminalText = raw.terminalText,
            ),
        )
        val allCandidates = resolved.candidates
        val resolvedReference = resolved.sessionReference
        val active = detection.hasActiveSession || allCandidates.isNotEmpty()
        return TerminalTabState(
            projectScopeId = scopeId,
            terminalTabId = tabId,
            terminalDisplayName = title,
            agentType = detection.agentType,
            sessionReference = resolvedReference,
            sessionReferenceSource = sourceName(resolved),
            sessionReferenceConfidence = confidenceName(resolved),
            sessionCandidates = allCandidates.map { it.sessionReference },
            processId = processMetadata.processId,
            processCommand = processMetadata.processCommand,
            hasActiveAgentSession = active,
        )
    }

    private fun readProcessMetadata(widget: ShellTerminalWidget?): ProcessMetadata {
        val connector = runCatching { widget?.processTtyConnector }.getOrNull() ?: return ProcessMetadata(null, "")
        val methodNames = listOf("getCommandLine", "getCommand")
        var commandLine = ""
        methodNames.forEach { methodName ->
            val rawValue = invokeConnectorMethod(connector, methodName) ?: return@forEach
            val normalized = normalizeCommandLine(rawValue)
            if (normalized.isNotBlank()) {
                commandLine = normalized
            }
        }
        val pid = readProcessId(connector)
        return ProcessMetadata(pid, commandLine)
    }

    private fun invokeConnectorMethod(connector: Any, methodName: String): Any? {
        val method = connector.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?: return null
        return runCatching { method.invoke(connector) }.getOrNull()
    }

    private fun normalizeCommandLine(value: Any): String {
        return when (value) {
            is String -> value
            is Array<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
            is Iterable<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
            else -> value.toString()
        }
    }

    private fun readProcessId(connector: Any): Long? {
        val pidMethodNames = listOf("getPid", "pid", "getProcessId")
        pidMethodNames.forEach { methodName ->
            val raw = invokeConnectorMethod(connector, methodName) ?: return@forEach
            val pid = when (raw) {
                is Number -> raw.toLong()
                else -> raw.toString().toLongOrNull()
            }
            if (pid != null && pid > 0) {
                return pid
            }
        }
        val process = invokeConnectorMethod(connector, "getProcess") ?: return null
        val method = process.javaClass.methods.firstOrNull { it.name == "pid" && it.parameterCount == 0 } ?: return null
        val rawPid = runCatching { method.invoke(process) }.getOrNull() ?: return null
        return (rawPid as? Number)?.toLong()
    }

    private fun sourceName(
        resolved: ResolvedSessionReference,
    ): String? {
        if (resolved.source == SessionReferenceSource.UNKNOWN) {
            return null
        }
        if (resolved.sessionReference != null || resolved.candidates.isNotEmpty()) {
            return resolved.source.name
        }
        return null
    }

    private fun confidenceName(
        resolved: ResolvedSessionReference,
    ): String? {
        if (resolved.confidence == SessionReferenceConfidence.NONE) {
            return null
        }
        if (resolved.sessionReference != null || resolved.candidates.isNotEmpty()) {
            return resolved.confidence.name
        }
        return null
    }
}
