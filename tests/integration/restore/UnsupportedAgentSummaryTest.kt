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

class UnsupportedAgentSummaryTest {
    @Test
    fun `unsupported terminal does not yield a success summary`() {
        // Registry has no adapter for the requested agent type, so the registry
        // resolves an UnsupportedAgentAdapter that emits UNSUPPORTED.
        val telemetry = RestoreTelemetryLogger()
        val coordinator = RestoreCoordinator(
            AgentAdapterRegistry(emptyList()),
            RestoreAttemptRunner(),
            RetryPolicyEngine(),
            RestoreSummaryNotifier(),
            telemetry,
        )
        val snapshots = listOf(snapshot("tab-unknown", "totally-unknown-agent"))

        val results = coordinator.restore("project-a", snapshots, timeoutSeconds = 30)

        assertTrue(results.all { it.status == "UNSUPPORTED" }, "expected only UNSUPPORTED attempts")

        val summary = telemetry.allEvents().single { it.eventType == "restore.summary" }
        assertEquals(
            "partial",
            summary.status,
            "UNSUPPORTED terminals must not be reported as a successful restore run",
        )
    }

    @Test
    fun `fully successful run still reports success`() {
        val telemetry = RestoreTelemetryLogger()
        val coordinator = RestoreCoordinator(
            AgentAdapterRegistry(listOf(CodexAgentAdapter())),
            RestoreAttemptRunner(),
            RetryPolicyEngine(),
            RestoreSummaryNotifier(),
            telemetry,
        )
        val snapshots = listOf(snapshot("tab-codex", "codex", "cx-1"))

        coordinator.restore("project-a", snapshots, timeoutSeconds = 30)

        val summary = telemetry.allEvents().single { it.eventType == "restore.summary" }
        assertEquals("success", summary.status)
    }

    private fun snapshot(tabId: String, agent: String, sessionReference: String? = "ref-$tabId"): TerminalSessionSnapshot {
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
