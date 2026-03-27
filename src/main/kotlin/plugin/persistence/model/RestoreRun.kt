package plugin.persistence.model

import kotlinx.serialization.Serializable

@Serializable
data class RestoreRun(
    val restoreRunId: String,
    val projectScopeId: String,
    val triggerType: String,
    val startedAt: Long,
    val finishedAt: Long,
    val totalCandidates: Int,
    val totalResumed: Int,
    val totalFailed: Int,
    val totalSkipped: Int,
)
