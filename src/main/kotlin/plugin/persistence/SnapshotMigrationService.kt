package plugin.persistence

class SnapshotMigrationService {
    fun ensureCurrentSchema(raw: String, currentVersion: Int): String {
        val serializer = SnapshotSerializer()
        val envelope = serializer.deserialize(raw)
        val migratedSnapshots = envelope.snapshots.map { snapshot ->
            if (snapshot.schemaVersion >= currentVersion) {
                snapshot
            } else {
                snapshot.copy(schemaVersion = currentVersion)
            }
        }
        val migrated = envelope.copy(
            schemaVersion = maxOf(envelope.schemaVersion, currentVersion),
            snapshots = migratedSnapshots,
        )
        return serializer.serialize(migrated)
    }
}
