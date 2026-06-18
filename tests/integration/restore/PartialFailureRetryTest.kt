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

class PartialFailureRetryTest {
    @Test
    fun `continues processing remaining terminals when one fails`() {
        val coordinator = RestoreCoordinator(
            AgentAdapterRegistry(listOf(CodexAgentAdapter())),
            RestoreAttemptRunner(),
            RetryPolicyEngine(),
            RestoreSummaryNotifier(),
            RestoreTelemetryLogger(),
        )
        val snapshots = listOf(
            TerminalSessionSnapshot(
                snapshotId = "s1",
                schemaVersion = 3,
                projectScopeId = "project-a",
                terminalTabId = "t1",
                terminalDisplayName = "T1",
                agentType = "codex",
                sessionReference = "session-1",
                sessionReferenceSource = "COMMAND_LINE",
                sessionReferenceConfidence = "HIGH",
                sessionCandidates = listOf("session-1"),
                processId = null,
                processCommand = null,
                hadActiveAgentSession = true,
                capturedAt = 1,
                lastKnownStatus = "ready",
            ),
            TerminalSessionSnapshot(
                snapshotId = "s2",
                schemaVersion = 3,
                projectScopeId = "project-a",
                terminalTabId = "t2",
                terminalDisplayName = "T2",
                agentType = "codex",
                sessionReference = "detected-codex-legacy",
                sessionReferenceSource = "COMMAND_LINE",
                sessionReferenceConfidence = "LOW",
                sessionCandidates = listOf("detected-codex-legacy"),
                processId = null,
                processCommand = null,
                hadActiveAgentSession = true,
                capturedAt = 1,
                lastKnownStatus = "ready",
            ),
        )

        val results = coordinator.restore("project-a", snapshots, timeoutSeconds = 30)

        assertTrue(results.any { it.terminalTabId == "t1" && it.status == "SUCCESS" })
        assertTrue(results.any { it.terminalTabId == "t2" && it.status != "SUCCESS" })
        assertEquals(2, results.size)
    }
}
