package tests.integration.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.AgentAdapterRegistry
import plugin.adapters.AgentType
import plugin.adapters.providers.ClaudeAgentAdapter
import plugin.adapters.providers.CopilotAgentAdapter
import plugin.adapters.providers.CodexAgentAdapter
import plugin.adapters.providers.OpenCodeAgentAdapter
import plugin.persistence.model.TerminalSessionSnapshot
import plugin.restore.RestoreAttemptRunner
import plugin.restore.RestoreCoordinator
import plugin.restore.RetryPolicyEngine
import plugin.restore.ui.RestoreSummaryNotifier
import plugin.telemetry.RestoreTelemetryLogger

class MixedAgentMappingTest {
    @Test
    fun `resolves correct adapter for mixed agent types`() {
        val registry = AgentAdapterRegistry(
            listOf(CodexAgentAdapter(), ClaudeAgentAdapter(), OpenCodeAgentAdapter(), CopilotAgentAdapter()),
        )

        val codex = registry.resolve("codex")
        val claude = registry.resolve("claude")
        val openCode = registry.resolve("opencode")
        val copilot = registry.resolve("copilot")

        assertEquals(AgentType.CODEX, codex.agentType)
        assertEquals(AgentType.CLAUDE, claude.agentType)
        assertEquals(AgentType.OPENCODE, openCode.agentType)
        assertEquals(AgentType.COPILOT, copilot.agentType)
    }

    @Test
    fun `restores exact and fallback sessions in one startup run`() {
        val registry = AgentAdapterRegistry(
            listOf(CodexAgentAdapter(), ClaudeAgentAdapter(), OpenCodeAgentAdapter(), CopilotAgentAdapter()),
        )
        val coordinator = RestoreCoordinator(
            registry,
            RestoreAttemptRunner(),
            RetryPolicyEngine(),
            RestoreSummaryNotifier(),
            RestoreTelemetryLogger(),
        )
        val commands = mutableMapOf<String, String>()
        val snapshots = listOf(
            snapshot("tab-codex-exact", "codex", "cx-123"),
            snapshot("tab-claude-fallback", "claude", null),
            snapshot("tab-opencode-fallback", "opencode", null),
            snapshot("tab-copilot-exact", "copilot", "cp-999"),
        )
        val executors = snapshots.associate { snapshot ->
            snapshot.terminalTabId to { command: String ->
                commands[snapshot.terminalTabId] = command
                true
            }
        }

        val results = coordinator.restore("project-a", snapshots, timeoutSeconds = 30, terminalCommandExecutors = executors)

        assertTrue(results.all { it.status == "SUCCESS" })
        assertEquals("codex resume 'cx-123'", commands["tab-codex-exact"])
        assertEquals("claude --continue", commands["tab-claude-fallback"])
        assertEquals("opencode --continue", commands["tab-opencode-fallback"])
        assertEquals("copilot --resume='cp-999'", commands["tab-copilot-exact"])
    }

    private fun snapshot(tabId: String, agent: String, sessionReference: String?): TerminalSessionSnapshot {
        return TerminalSessionSnapshot(
            snapshotId = "snap-$tabId",
            schemaVersion = 3,
            projectScopeId = "project-a",
            terminalTabId = tabId,
            terminalDisplayName = tabId,
            agentType = agent,
            sessionReference = sessionReference,
            sessionReferenceSource = if (sessionReference == null) null else "COMMAND_LINE",
            sessionReferenceConfidence = if (sessionReference == null) null else "HIGH",
            sessionCandidates = listOfNotNull(sessionReference),
            processId = null,
            processCommand = null,
            hadActiveAgentSession = true,
            capturedAt = 1L,
            lastKnownStatus = "ready",
        )
    }
}
