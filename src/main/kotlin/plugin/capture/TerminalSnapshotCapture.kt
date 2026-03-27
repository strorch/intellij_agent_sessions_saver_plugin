package plugin.capture

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
import plugin.runtime.RestoreRuntimeFactory

data class CaptureSummary(
    val totalTabs: Int,
    val capturedSnapshots: Int,
    val activeSessions: Int,
)

object TerminalSnapshotCapture {
    private val resolverChain = ResolverChain()

    fun capture(project: Project): CaptureSummary {
        val runtime = RestoreRuntimeFactory.forProject(project)
        val scopeId = runtime.projectScopeId
        val widgets = TerminalToolWindowManager.getInstance(project).terminalWidgets.toList()
        val tabs = widgets.mapIndexed { index, widget ->
            toTabState(scopeId, project.basePath, index, ShellTerminalWidget.asShellJediTermWidget(widget))
        }
        val captureService = SessionSnapshotCaptureService(TerminalTabMapper())
        val snapshots = captureService.capture(scopeId, tabs)
        runtime.snapshotStore.save(scopeId, schemaVersion = 3, snapshots = snapshots)
        return CaptureSummary(
            totalTabs = widgets.size,
            capturedSnapshots = snapshots.size,
            activeSessions = snapshots.count { it.hadActiveAgentSession },
        )
    }

    private fun toTabState(
        scopeId: String,
        projectBasePath: String?,
        index: Int,
        widget: ShellTerminalWidget?,
    ): TerminalTabState {
        val title = widget?.terminalTitle?.buildTitle()
            ?.takeIf { it.isNotBlank() }
            ?: "Terminal ${index + 1}"
        val tabId = "terminal-${index + 1}-${title.hashCode().toUInt().toString(16)}"
        val commandText = widget?.typedShellCommand.orEmpty()
        val shellCommand = widget?.shellCommand?.joinToString(" ").orEmpty()
        val terminalText = widget?.text.orEmpty()
        val processMetadata = readProcessMetadata(widget)
        val processCommandLine = processMetadata.processCommand
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = processCommandLine,
                typedCommand = commandText,
                shellCommand = shellCommand,
                terminalText = terminalText,
            ),
        )
        val resolved = resolverChain.resolve(
            ResolverContext(
                agentType = detection.agentType,
                projectBasePath = projectBasePath,
                processMetadata = processMetadata,
                processCommandLine = processCommandLine,
                typedCommand = commandText,
                shellCommand = shellCommand,
                terminalText = terminalText,
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
