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

/**
 * Marshals the actual terminal-widget dispatch onto the thread that is allowed to touch IntelliJ
 * terminal widget APIs (the EDT in production). See [RestoreAttemptRunner] for the threading model.
 */
fun interface TerminalDispatchMarshaller {
    /**
     * Runs [dispatch] on the UI/EDT thread and returns its boolean result. Implementations must
     * block the *calling* (worker) thread until the dispatch completes so the caller can measure
     * and time-bound it; they must never block the EDT itself.
     */
    fun dispatchOnUiThread(dispatch: () -> Boolean): Boolean

    companion object {
        /**
         * Inline marshaller used by tests and non-IDE contexts: runs the dispatch on the calling
         * thread. Production wiring supplies an EDT-marshaling implementation.
         */
        val INLINE: TerminalDispatchMarshaller = TerminalDispatchMarshaller { it() }
    }
}

class RestoreAttemptRunner(
    private val dispatchMarshaller: TerminalDispatchMarshaller = TerminalDispatchMarshaller.INLINE,
) {
    /**
     * Threading model (addresses PR #18 review feedback):
     *
     * The per-attempt 30s budget (FR-015/SC-005) is enforced by running [AgentAdapter.resume] on a
     * bounded single-use worker thread and waiting on the resulting [java.util.concurrent.Future]
     * with a timeout. This keeps the timeout truly enforceable instead of depending on the dispatch
     * call returning quickly.
     *
     * However, the adapter ultimately invokes [commandExecutor], which dispatches commands through
     * IntelliJ terminal widget APIs (e.g. ShellTerminalWidget#sendCommandToExecute). Those APIs are
     * NOT thread-safe and must run on the EDT. To avoid touching widget APIs from the worker thread,
     * the [commandExecutor] handed to the adapter is wrapped so its body is marshaled to the EDT via
     * [dispatchMarshaller]. The worker thread blocks while the EDT runs the dispatch, so:
     *   - terminal widget APIs always execute on the EDT (thread-safe), and
     *   - a hung dispatch still trips the [Future.get] timeout on the calling thread and is reported
     *     as TIMEOUT.
     *
     * Because the worker blocks on the EDT for the dispatch, the caller that waits on [Future.get]
     * must NOT be the EDT (otherwise the EDT would be unable to service the marshaled dispatch).
     * Restore orchestration therefore runs on a background/pooled thread; see the callers in
     * StartupRestorePostStartupActivity, ManualRestoreIdeAction and RetryFailedTerminalIdeAction.
     */
    fun runAttempt(
        restoreRunId: String,
        snapshot: TerminalSessionSnapshot,
        adapter: AgentAdapter,
        timeoutSeconds: Int,
        attemptIndex: Int,
        commandExecutor: ((String) -> Boolean)? = null,
    ): RestoreAttemptResult {
        // Wrap the executor so the widget-touching body runs on the EDT, not on the worker thread.
        val marshaledExecutor: ((String) -> Boolean)? = commandExecutor?.let { raw ->
            { command -> dispatchMarshaller.dispatchOnUiThread { raw(command) } }
        }

        val request = AgentResumeRequest(
            snapshot.projectScopeId,
            snapshot.terminalTabId,
            snapshot.sessionReference.orEmpty(),
            timeoutSeconds,
            marshaledExecutor,
        )

        val startedAt = System.currentTimeMillis()
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
