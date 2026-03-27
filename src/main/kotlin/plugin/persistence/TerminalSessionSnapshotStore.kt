package plugin.persistence

import plugin.persistence.model.TerminalSessionSnapshot
import java.io.File

class TerminalSessionSnapshotStore(
    private val serializer: SnapshotSerializer,
    private val migrationService: SnapshotMigrationService,
    private val baseDir: File,
) {
    fun save(
        projectScopeId: String,
        schemaVersion: Int,
        snapshots: List<TerminalSessionSnapshot>,
    ) {
        val envelope = SnapshotEnvelope(schemaVersion, projectScopeId, snapshots)
        val file = snapshotFile(projectScopeId)
        file.parentFile?.mkdirs()
        file.writeText(serializer.serialize(envelope))
    }

    fun load(projectScopeId: String, currentSchemaVersion: Int): List<TerminalSessionSnapshot> {
        val file = snapshotFile(projectScopeId)
        if (!file.exists()) {
            return emptyList()
        }
        val migrated = migrationService.ensureCurrentSchema(file.readText(), currentSchemaVersion)
        val envelope = serializer.deserialize(migrated)
        return envelope.snapshots
    }

    private fun snapshotFile(projectScopeId: String): File {
        return File(baseDir, "$projectScopeId-snapshots.json")
    }
}
