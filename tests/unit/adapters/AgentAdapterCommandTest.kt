package tests.unit.adapters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeStatus
import plugin.adapters.providers.ClaudeAgentAdapter
import plugin.adapters.providers.CopilotAgentAdapter
import plugin.adapters.providers.CodexAgentAdapter
import plugin.adapters.providers.OpenCodeAgentAdapter

class AgentAdapterCommandTest {
    @Test
    fun `codex adapter resumes without explicit id`() {
        var dispatched = ""
        val adapter = CodexAgentAdapter()
        val result = adapter.resume(request(sessionReference = "") { command -> dispatched = command; true })

        assertEquals(AgentResumeStatus.SUCCESS, result.status)
        assertEquals("codex resume --last", dispatched)
    }

    @Test
    fun `claude adapter resumes without explicit id`() {
        var dispatched = ""
        val adapter = ClaudeAgentAdapter()
        val result = adapter.resume(request(sessionReference = "") { command -> dispatched = command; true })

        assertEquals(AgentResumeStatus.SUCCESS, result.status)
        assertEquals("claude --continue", dispatched)
    }

    @Test
    fun `opencode adapter includes explicit id`() {
        var dispatched = ""
        val adapter = OpenCodeAgentAdapter()
        val result = adapter.resume(request(sessionReference = "oc-9") { command -> dispatched = command; true })

        assertEquals(AgentResumeStatus.SUCCESS, result.status)
        assertEquals("opencode --session oc-9", dispatched)
    }

    @Test
    fun `copilot adapter resumes without explicit id`() {
        var dispatched = ""
        val adapter = CopilotAgentAdapter()
        val result = adapter.resume(request(sessionReference = "") { command -> dispatched = command; true })

        assertEquals(AgentResumeStatus.SUCCESS, result.status)
        assertEquals("copilot --continue", dispatched)
    }

    @Test
    fun `synthetic ids are skipped`() {
        val adapter = CodexAgentAdapter()
        val result = adapter.resume(request(sessionReference = "detected-codex-1") { true })

        assertEquals(AgentResumeStatus.SKIPPED, result.status)
        assertTrue(result.failureCode == "SYNTHETIC_ID")
    }

    private fun request(
        sessionReference: String,
        executor: (String) -> Boolean,
    ): AgentResumeRequest {
        return AgentResumeRequest(
            projectScopeId = "project-a",
            terminalTabId = "t1",
            sessionReference = sessionReference,
            timeoutSeconds = 30,
            commandExecutor = executor,
        )
    }
}
