package plugin.persistence.model

import kotlinx.serialization.Serializable

@Serializable
data class RestoreAttemptResult(
    val attemptId: String,
    val restoreRunId: String,
    val terminalTabId: String,
    val attemptIndex: Int,
    val startedAt: Long,
    val endedAt: Long,
    val status: String,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)
