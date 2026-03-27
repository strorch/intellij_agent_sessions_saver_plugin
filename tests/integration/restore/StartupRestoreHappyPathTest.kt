package tests.integration.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.AgentAdapterRegistry
import plugin.adapters.TerminalTabMapper
import plugin.adapters.TerminalTabState
import plugin.adapters.providers.ClaudeAgentAdapter
import plugin.adapters.providers.CopilotAgentAdapter
import plugin.adapters.providers.CodexAgentAdapter
import plugin.adapters.providers.OpenCodeAgentAdapter
import plugin.persistence.SessionSnapshotCaptureService
import plugin.persistence.SnapshotMigrationService
import plugin.persistence.SnapshotSerializer
import plugin.persistence.TerminalSessionSnapshotStore
import plugin.restore.ProjectScopeGuard
import plugin.restore.RestoreAttemptRunner
import plugin.restore.RestoreCoordinator
import plugin.restore.RetryPolicyEngine
import plugin.restore.StartupRestoreActivity
import plugin.restore.ui.RestoreSummaryNotifier
import plugin.settings.RestorePolicySettingsService
import plugin.telemetry.RestoreTelemetryLogger

class StartupRestoreHappyPathTest {
    @Test
    fun `restores active sessions on startup`() {
        val tempDir = createTempDir(prefix = "startup-restore-")
        val store = TerminalSessionSnapshotStore(SnapshotSerializer(), SnapshotMigrationService(), tempDir)
        val capture = SessionSnapshotCaptureService(TerminalTabMapper())
        val tabs = listOf(
            TerminalTabState("project-a", "t1", "Tab1", "codex", "codex-1", "COMMAND_LINE", "HIGH", listOf("codex-1"), null, null, true),
            TerminalTabState("project-a", "t2", "Tab2", "claude", "claude-1", "COMMAND_LINE", "HIGH", listOf("claude-1"), null, null, true),
            TerminalTabState("project-a", "t3", "Tab3", "copilot", "copilot-1", "COMMAND_LINE", "HIGH", listOf("copilot-1"), null, null, true),
            TerminalTabState("project-a", "t4", "Tab4", "unknown", null, null, null, emptyList(), null, null, false),
        )
        store.save("project-a", 3, capture.capture("project-a", tabs))

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
        val activity = StartupRestoreActivity(store, RestorePolicySettingsService(), ProjectScopeGuard(), coordinator)

        val results = activity.runStartupRestore("project-a")

        assertEquals(3, results.size)
        assertTrue(results.all { it.status == "SUCCESS" })
        tempDir.deleteRecursively()
    }
}
