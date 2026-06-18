package tests.unit.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.AgentResumeStatus
import plugin.adapters.providers.CodexAgentAdapter
import plugin.restore.RestoreAttemptRunner
import plugin.restore.TerminalDispatchMarshaller
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression coverage for the PR #18 fix:
 *  - the per-attempt timeout is actually enforced when the dispatch hangs (FR-015/SC-005), and
 *  - terminal-widget dispatch is marshaled through the injected [TerminalDispatchMarshaller]
 *    (in production: onto the EDT) rather than run directly on the timeout worker thread.
 */
class RestoreAttemptTimeoutTest {
    @Test
    fun `slow dispatch that exceeds the budget is reported as TIMEOUT`() {
        // 1s budget; the command executor sleeps well past it to simulate a hung terminal dispatch.
        val runner = RestoreAttemptRunner()
        val adapter = CodexAgentAdapter()
        val slowExecutor: (String) -> Boolean = {
            Thread.sleep(3_000)
            true
        }

        val result = runner.runAttempt(
            restoreRunId = "run-timeout",
            snapshot = RestoreFixtureFactory.snapshot(),
            adapter = adapter,
            timeoutSeconds = 1,
            attemptIndex = 0,
            commandExecutor = slowExecutor,
        )

        assertEquals(AgentResumeStatus.TIMEOUT.name, result.status)
        assertEquals("TIMEOUT", result.failureCode)
        // Real measured duration is honored: the attempt is cut off near the budget, not after 3s.
        val elapsed = result.endedAt - result.startedAt
        assertTrue(elapsed in 900..2_500, "expected timeout near 1s budget but was ${elapsed}ms")
    }

    @Test
    fun `fast dispatch yields SUCCESS with a measured duration`() {
        val runner = RestoreAttemptRunner()
        val adapter = CodexAgentAdapter()
        val fastExecutor: (String) -> Boolean = { true }

        val result = runner.runAttempt(
            restoreRunId = "run-success",
            snapshot = RestoreFixtureFactory.snapshot(),
            adapter = adapter,
            timeoutSeconds = 30,
            attemptIndex = 0,
            commandExecutor = fastExecutor,
        )

        assertEquals(AgentResumeStatus.SUCCESS.name, result.status)
        assertNull(result.failureCode)
        assertTrue(result.endedAt >= result.startedAt)
    }

    @Test
    fun `terminal dispatch is marshaled through the dispatch marshaller, not the raw worker`() {
        val marshalCount = AtomicInteger(0)
        // A marshaller that records it was used and runs the dispatch on a dedicated thread,
        // standing in for the production EDT marshaller. The adapter must reach the widget call
        // only through this marshaller.
        val recordingMarshaller = TerminalDispatchMarshaller { dispatch ->
            marshalCount.incrementAndGet()
            var dispatchResult = false
            val thread = Thread { dispatchResult = dispatch() }
            thread.start()
            thread.join()
            dispatchResult
        }
        val runner = RestoreAttemptRunner(recordingMarshaller)
        val adapter = CodexAgentAdapter()

        val result = runner.runAttempt(
            restoreRunId = "run-marshal",
            snapshot = RestoreFixtureFactory.snapshot(),
            adapter = adapter,
            timeoutSeconds = 30,
            attemptIndex = 0,
            commandExecutor = { true },
        )

        assertEquals(AgentResumeStatus.SUCCESS.name, result.status)
        assertEquals(1, marshalCount.get(), "expected the widget dispatch to go through the marshaller exactly once")
    }
}
