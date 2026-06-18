package plugin.persistence.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TerminalSessionSnapshot(
    val snapshotId: String,
    val schemaVersion: Int,
    val projectScopeId: String,
    val terminalTabId: String,
    val terminalDisplayName: String,
    val agentType: String,
    val sessionReference: String?,
    val sessionReferenceSource: String? = null,
    val sessionReferenceConfidence: String? = null,
    val sessionCandidates: List<String> = emptyList(),
    val processId: Long? = null,
    // Privacy (FR-013): the full process command line may carry secrets (e.g. --api-key, tokens).
    // Keep it available transiently during capture/resolution, but never persist it to disk.
    @Transient
    val processCommand: String? = null,
    val hadActiveAgentSession: Boolean,
    val capturedAt: Long,
    val lastKnownStatus: String,
) {
    fun isValid(allowFallback: Boolean = false): Boolean {
        val hasScope = projectScopeId.isNotBlank()
        val hasTerminal = terminalTabId.isNotBlank()
        val hasExactReference = !sessionReference.isNullOrBlank()
        val hasSession = !hadActiveAgentSession || hasExactReference || allowFallback
        return hasScope && hasTerminal && hasSession
    }
}
