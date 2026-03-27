package plugin.restore

import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeStatus
import plugin.persistence.model.RestoreAttemptResult
import plugin.persistence.model.TerminalSessionSnapshot
import java.util.UUID

class RetryPolicyEngine(
    private val maxAttempts: Int = 2,
) {
    fun executeWithRetry(
        snapshot: TerminalSessionSnapshot,
        adapter: AgentAdapter,
        timeoutSeconds: Int,
        attemptRunner: RestoreAttemptRunner,
        commandExecutor: ((String) -> Boolean)? = null,
    ): List<RestoreAttemptResult> {
        val restoreRunId = UUID.randomUUID().toString()
        val first = attemptRunner.runAttempt(restoreRunId, snapshot, adapter, timeoutSeconds, 1, commandExecutor)
        if (first.status == AgentResumeStatus.SUCCESS.name || maxAttempts <= 1) {
            return listOf(first)
        }
        val second = attemptRunner.runAttempt(restoreRunId, snapshot, adapter, timeoutSeconds, 2, commandExecutor)
        return listOf(first, second)
    }
}
