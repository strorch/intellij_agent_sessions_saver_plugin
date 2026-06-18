package tests.integration.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.AgentAdapterRegistry
import plugin.adapters.providers.CodexAgentAdapter
import plugin.persistence.model.TerminalSessionSnapshot
import plugin.restore.RestoreAttemptRunner
import plugin.restore.RestoreCoordinator
import plugin.restore.RetryPolicyEngine
import plugin.restore.ui.RestoreSummaryNotifier
import plugin.telemetry.RestoreTelemetryLogger

class StartupRestoreAmbiguityChooserTest {
    @Test
    fun `restores selected exact id for ambiguous tab`() {
        val coordinator = RestoreCoordinator(
            AgentAdapterRegistry(listOf(CodexAgentAdapter())),
            RestoreAttemptRunner(),
            RetryPolicyEngine(),
            RestoreSummaryNotifier(),
            RestoreTelemetryLogger(),
        )
        var dispatched = ""
        val snapshot = TerminalSessionSnapshot(
            snapshotId = "snap-t1",
            schemaVersion = 3,
            projectScopeId = "project-a",
            terminalTabId = "t1",
            terminalDisplayName = "Tab 1",
            agentType = "codex",
            sessionReference = "chosen-777",
            sessionReferenceSource = "CHOOSER",
            sessionReferenceConfidence = "HIGH",
            sessionCandidates = listOf("a-1", "chosen-777"),
            processId = null,
            processCommand = null,
            hadActiveAgentSession = true,
            capturedAt = 1L,
            lastKnownStatus = "ready",
        )

        val results = coordinator.restore(
            "project-a",
            listOf(snapshot),
            timeoutSeconds = 30,
            terminalCommandExecutors = mapOf("t1" to { command: String -> dispatched = command; true }),
        )

        assertEquals("codex resume 'chosen-777'", dispatched)
        assertTrue(results.last().status == "SUCCESS")
    }
}
