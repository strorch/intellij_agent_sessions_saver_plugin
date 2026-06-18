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

class StartupRestoreAmbiguityFallbackTest {
    @Test
    fun `uses fallback command when chooser is canceled for ambiguous tab`() {
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
            sessionReference = null,
            sessionReferenceSource = "CHOOSER",
            sessionReferenceConfidence = null,
            sessionCandidates = listOf("a-1", "b-2"),
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

        assertEquals("codex resume --last", dispatched)
        assertTrue(results.last().status == "SUCCESS")
    }
}
