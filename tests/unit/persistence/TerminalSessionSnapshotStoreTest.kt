package tests.unit.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.adapters.TerminalTabMapper
import plugin.adapters.TerminalTabState
import plugin.persistence.SessionSnapshotCaptureService
import plugin.persistence.SnapshotMigrationService
import plugin.persistence.SnapshotSerializer
import plugin.persistence.TerminalSessionSnapshotStore
import java.io.File

class TerminalSessionSnapshotStoreTest {
    @Test
    fun `captures only mapped terminal states and reloads persisted snapshots`() {
        val capture = SessionSnapshotCaptureService(TerminalTabMapper())
        val tabs = listOf(
            TerminalTabState("project-a", "tab-1", "Tab 1", "codex", "s1", "COMMAND_LINE", "HIGH", listOf("s1"), null, null, true),
            TerminalTabState("project-a", "tab-2", "Tab 2", "unknown", null, null, null, emptyList(), null, null, false),
        )
        val snapshots = capture.capture("project-a", tabs)
        val tempDir = createTempDir(prefix = "snapshot-store-")
        val store = TerminalSessionSnapshotStore(SnapshotSerializer(), SnapshotMigrationService(), tempDir)

        store.save("project-a", 3, snapshots)
        val loaded = store.load("project-a", 3)

        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it.sessionReference == null })
        tempDir.deleteRecursively()
    }
}
