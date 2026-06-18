package plugin.adapters.providers

import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeResult
import plugin.adapters.AgentResumeStatus
import plugin.adapters.AgentType

class ClaudeAgentAdapter : AgentAdapter {
    override val agentType: AgentType = AgentType.CLAUDE

    override fun resume(request: AgentResumeRequest): AgentResumeResult {
        if (request.sessionReference.startsWith("detected-")) {
            return AgentResumeResult(AgentResumeStatus.SKIPPED, 0, "SYNTHETIC_ID", "Synthetic session id cannot be resumed")
        }
        val commandExecutor = request.commandExecutor
        if (commandExecutor != null) {
            val command = buildResumeCommand(request.sessionReference)
            val dispatched = commandExecutor(command)
            if (!dispatched) {
                return AgentResumeResult(AgentResumeStatus.FAILED, 0, "DISPATCH_FAILED", "Failed to send Claude resume command")
            }
        }
        return AgentResumeResult(AgentResumeStatus.SUCCESS, 10)
    }

    private fun buildResumeCommand(sessionReference: String): String {
        if (sessionReference.isBlank()) {
            return "claude --continue"
        }
        return "claude --resume ${escapeArgument(sessionReference)}"
    }

    private fun escapeArgument(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
