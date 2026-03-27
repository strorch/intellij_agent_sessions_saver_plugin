package plugin.restore

import plugin.adapters.AgentAdapterRegistry
import plugin.adapters.AgentResumeStatus
import plugin.persistence.model.RestoreAttemptResult
import plugin.persistence.model.TerminalSessionSnapshot
import plugin.restore.ui.RestoreSummaryNotifier
import plugin.telemetry.RestoreTelemetryEvent
import plugin.telemetry.RestoreTelemetryLogger

class RestoreCoordinator(
    private val adapterRegistry: AgentAdapterRegistry,
    private val attemptRunner: RestoreAttemptRunner,
    private val retryPolicyEngine: RetryPolicyEngine,
    private val notifier: RestoreSummaryNotifier,
    private val telemetryLogger: RestoreTelemetryLogger,
) {
    fun restore(
        projectScopeId: String,
        snapshots: List<TerminalSessionSnapshot>,
        timeoutSeconds: Int,
        terminalCommandExecutors: Map<String, (String) -> Boolean> = emptyMap(),
    ): List<RestoreAttemptResult> {
        val results = mutableListOf<RestoreAttemptResult>()
        telemetryLogger.log(RestoreTelemetryEvent("restore.start", projectScopeId, details = "candidates=${snapshots.size}"))
        snapshots.filter { it.projectScopeId == projectScopeId }
            .forEach { snapshot ->
                val adapter = adapterRegistry.resolve(snapshot.agentType)
                val commandExecutor = terminalCommandExecutors[snapshot.terminalTabId]
                val terminalResults = retryPolicyEngine.executeWithRetry(
                    snapshot,
                    adapter,
                    timeoutSeconds,
                    attemptRunner,
                    commandExecutor,
                )
                results.addAll(terminalResults)
                val final = terminalResults.lastOrNull()
                telemetryLogger.log(
                    RestoreTelemetryEvent(
                        eventType = "restore.terminal",
                        projectScopeId = projectScopeId,
                        terminalTabId = snapshot.terminalTabId,
                        status = final?.status,
                        details = "attempts=${terminalResults.size}",
                    ),
                )
            }
        val failures = results.count { it.status != AgentResumeStatus.SUCCESS.name }
        notifier.publish(results)
        telemetryLogger.log(RestoreTelemetryEvent("restore.summary", projectScopeId, status = if (failures == 0) "success" else "partial"))
        telemetryLogger.log(RestoreTelemetryEvent("restore.end", projectScopeId, details = "results=${results.size}"))
        return results
    }
}
