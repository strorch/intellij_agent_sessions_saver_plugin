package tests.unit.persistence

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import plugin.persistence.SnapshotEnvelope
import plugin.persistence.SnapshotSerializer
import tests.unit.restore.RestoreFixtureFactory

class SnapshotPrivacyContractTest {
    @Test
    fun `serialized snapshot does not include prohibited sensitive fields`() {
        val envelope = SnapshotEnvelope(
            schemaVersion = 3,
            projectScopeId = "project-a",
            snapshots = listOf(RestoreFixtureFactory.snapshot()),
        )

        val raw = SnapshotSerializer().serialize(envelope)

        assertFalse(raw.contains("token", ignoreCase = true))
        assertFalse(raw.contains("commandHistory", ignoreCase = true))
        assertFalse(raw.contains("terminalOutput", ignoreCase = true))
    }
}
