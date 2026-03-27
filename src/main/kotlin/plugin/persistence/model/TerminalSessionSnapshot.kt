package plugin.persistence.model

import kotlinx.serialization.Serializable

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
