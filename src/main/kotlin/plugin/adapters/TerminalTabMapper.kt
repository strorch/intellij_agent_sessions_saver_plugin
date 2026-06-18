package plugin.adapters

data class TerminalTabState(
    val projectScopeId: String,
    val terminalTabId: String,
    val terminalDisplayName: String,
    val agentType: String,
    val sessionReference: String?,
    val sessionReferenceSource: String?,
    val sessionReferenceConfidence: String?,
    val sessionCandidates: List<String>,
    val processId: Long?,
    val processCommand: String?,
    val hasActiveAgentSession: Boolean,
)

class TerminalTabMapper {
    fun toSessionState(rawTabs: List<TerminalTabState>): List<TerminalTabState> {
        return rawTabs.distinctBy { it.terminalTabId }
    }
}
