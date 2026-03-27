package tests.unit.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeResult
import plugin.adapters.AgentResumeStatus
import plugin.adapters.AgentType
import plugin.restore.RestoreAttemptRunner
import plugin.restore.RetryPolicyEngine

class RetryAndTimeoutPolicyTest {
    @Test
    fun `performs one retry when first attempt fails`() {
        val runner = RestoreAttemptRunner()
        val policy = RetryPolicyEngine()
        val adapter = object : AgentAdapter {
            override val agentType: AgentType = AgentType.CODEX
            private var call = 0
            override fun resume(request: AgentResumeRequest): AgentResumeResult {
                call += 1
                return if (call == 1) {
                    AgentResumeResult(AgentResumeStatus.FAILED, 5, "MISSING", "first failure")
                } else {
                    AgentResumeResult(AgentResumeStatus.SUCCESS, 5)
                }
            }
        }
        val results = policy.executeWithRetry(RestoreFixtureFactory.snapshot(), adapter, 30, runner)
        assertEquals(2, results.size)
        assertEquals("FAILED", results.first().status)
        assertEquals("SUCCESS", results.last().status)
    }
}
