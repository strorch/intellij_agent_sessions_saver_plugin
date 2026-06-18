package plugin.adapters

interface AgentAdapter {
    val agentType: AgentType

    fun resume(request: AgentResumeRequest): AgentResumeResult
}
