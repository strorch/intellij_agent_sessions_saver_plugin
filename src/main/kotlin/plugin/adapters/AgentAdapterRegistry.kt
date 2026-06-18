package plugin.adapters

class AgentAdapterRegistry(
    adapters: List<AgentAdapter> = emptyList(),
) {
    private val adaptersByType = adapters.associateBy { it.agentType }

    fun resolve(agentTypeRaw: String): AgentAdapter {
        val mappedType = when (agentTypeRaw.lowercase()) {
            "codex" -> AgentType.CODEX
            "claude" -> AgentType.CLAUDE
            "opencode" -> AgentType.OPENCODE
            "copilot", "github-copilot", "gh-copilot" -> AgentType.COPILOT
            else -> AgentType.UNKNOWN
        }
        return adaptersByType[mappedType] ?: UnsupportedAgentAdapter(mappedType)
    }

    private class UnsupportedAgentAdapter(override val agentType: AgentType) : AgentAdapter {
        override fun resume(request: AgentResumeRequest): AgentResumeResult {
            return AgentResumeResult(
                status = AgentResumeStatus.UNSUPPORTED,
                durationMs = 0,
                failureCode = "UNSUPPORTED_AGENT",
                failureMessage = "No adapter registered",
            )
        }
    }
}
