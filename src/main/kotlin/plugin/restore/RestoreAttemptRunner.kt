package plugin.restore

import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeResult
import plugin.adapters.AgentResumeStatus
import plugin.persistence.model.RestoreAttemptResult
import plugin.persistence.model.TerminalSessionSnapshot
import java.util.UUID
import kotlin.system.measureTimeMillis

class RestoreAttemptRunner {
    fun runAttempt(
        restoreRunId: String,
        snapshot: TerminalSessionSnapshot,
        adapter: AgentAdapter,
        timeoutSeconds: Int,
        attemptIndex: Int,
        commandExecutor: ((String) -> Boolean)? = null,
    ): RestoreAttemptResult {
        var adapterResult = AgentResumeResult(AgentResumeStatus.FAILED, 0, "NO_RESULT", "No adapter result")
        val startedAt = System.currentTimeMillis()
        val measured = measureTimeMillis {
            adapterResult = adapter.resume(
                AgentResumeRequest(
                    snapshot.projectScopeId,
                    snapshot.terminalTabId,
                    snapshot.sessionReference.orEmpty(),
                    timeoutSeconds,
                    commandExecutor,
                ),
            )
        }
        val endedAt = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L
        val finalStatus = if (measured > timeoutMs) AgentResumeStatus.TIMEOUT else adapterResult.status
        return RestoreAttemptResult(
            attemptId = UUID.randomUUID().toString(),
            restoreRunId = restoreRunId,
            terminalTabId = snapshot.terminalTabId,
            attemptIndex = attemptIndex,
            startedAt = startedAt,
            endedAt = endedAt,
            status = finalStatus.name,
            failureCode = if (finalStatus == AgentResumeStatus.TIMEOUT) "TIMEOUT" else adapterResult.failureCode,
            failureMessage = if (finalStatus == AgentResumeStatus.TIMEOUT) "Resume timed out" else adapterResult.failureMessage,
        )
    }
}
