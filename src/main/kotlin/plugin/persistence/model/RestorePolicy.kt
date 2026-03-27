package plugin.persistence.model

import kotlinx.serialization.Serializable

@Serializable
data class RestorePolicy(
    val autoRestoreEnabled: Boolean = true,
    val manualRestoreMode: Boolean = false,
    val autoRetryCount: Int = 1,
    val attemptTimeoutSeconds: Int = 30,
    val scopeBoundary: String = "project-only",
)
