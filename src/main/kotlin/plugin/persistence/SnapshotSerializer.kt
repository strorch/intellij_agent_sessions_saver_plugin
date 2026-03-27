package plugin.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plugin.persistence.model.TerminalSessionSnapshot

@Serializable
data class SnapshotEnvelope(
    val schemaVersion: Int,
    val projectScopeId: String,
    val snapshots: List<TerminalSessionSnapshot>,
)

class SnapshotSerializer {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun serialize(envelope: SnapshotEnvelope): String {
        return json.encodeToString(SnapshotEnvelope.serializer(), envelope)
    }

    fun deserialize(raw: String): SnapshotEnvelope {
        return json.decodeFromString(SnapshotEnvelope.serializer(), raw)
    }
}
