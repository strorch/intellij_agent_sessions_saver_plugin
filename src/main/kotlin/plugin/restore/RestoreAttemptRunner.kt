package plugin.restore

import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeResult
import plugin.adapters.AgentResumeStatus
import plugin.persistence.model.RestoreAttemptResult
import plugin.persistence.model.TerminalSessionSnapshot
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RestoreAttemptRunner {
    fun runAttempt(
        restoreRunId: String,
        snapshot: TerminalSessionSnapshot,
        adapter: AgentAdapter,
        timeoutSeconds: Int,
        attemptIndex: Int,
        commandExecutor: ((String) -> Boolean)? = null,
    ): RestoreAttemptResult {
        val request = AgentResumeRequest(
            snapshot.projectScopeId,
            snapshot.terminalTabId,
            snapshot.sessionReference.orEmpty(),
            timeoutSeconds,
            commandExecutor,
        )

        val startedAt = System.currentTimeMillis()
        // FR-015/SC-005: a single resume attempt must not exceed the configured per-attempt
        // budget (30s by default). The adapter call (dispatch + whatever confirmation it can
        // observe) runs on a bounded single-use executor so the timeout is actually enforceable
        // rather than depending on the dispatch call returning quickly. If the budget is exceeded
        // the in-flight work is interrupted and the attempt is reported as TIMEOUT.
        val executor = Executors.newSingleThreadExecutor()
        val timeoutMs = timeoutSeconds * 1000L
        var adapterResult = AgentResumeResult(AgentResumeStatus.FAILED, 0, "NO_RESULT", "No adapter result")
        var timedOut = false
        try {
            val future = executor.submit(Callable { adapter.resume(request) })
            try {
                adapterResult = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (timeout: TimeoutException) {
                future.cancel(true)
                timedOut = true
            }
        } finally {
            executor.shutdownNow()
        }
        val endedAt = System.currentTimeMillis()

        // Duration is the real elapsed time of the attempt, never a hardcoded value.
        val elapsedMs = endedAt - startedAt
        val finalStatus = if (timedOut || elapsedMs > timeoutMs) AgentResumeStatus.TIMEOUT else adapterResult.status
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
