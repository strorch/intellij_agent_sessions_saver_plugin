package tests.unit.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.persistence.SnapshotMigrationService
import plugin.persistence.SnapshotSerializer
import java.io.File

class SnapshotMigrationCompatibilityTest {
    @Test
    fun `migrates v1 fixture to schema v3`() {
        val fixture = File("tests/fixtures/snapshots/v1-minimal.json").readText()
        val migrated = SnapshotMigrationService().ensureCurrentSchema(fixture, currentVersion = 3)
        val envelope = SnapshotSerializer().deserialize(migrated)

        assertEquals(3, envelope.schemaVersion)
        assertEquals(1, envelope.snapshots.size)
        assertEquals(3, envelope.snapshots.first().schemaVersion)
        assertEquals(0, envelope.snapshots.first().sessionCandidates.size)
    }

    @Test
    fun `migrates v2 fixture to schema v3 with defaults`() {
        val fixture = File("tests/fixtures/snapshots/v2-current.json").readText()
        val migrated = SnapshotMigrationService().ensureCurrentSchema(fixture, currentVersion = 3)
        val envelope = SnapshotSerializer().deserialize(migrated)

        assertEquals(3, envelope.schemaVersion)
        assertEquals(3, envelope.snapshots.first().schemaVersion)
        assertEquals(null, envelope.snapshots.first().sessionReferenceSource)
        assertEquals(0, envelope.snapshots.first().sessionCandidates.size)
    }
}
