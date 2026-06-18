package plugin.adapters.providers

import plugin.adapters.AgentAdapter
import plugin.adapters.AgentResumeRequest
import plugin.adapters.AgentResumeResult
import plugin.adapters.AgentResumeStatus
import plugin.adapters.AgentType
import kotlin.system.measureTimeMillis

class OpenCodeAgentAdapter : AgentAdapter {
    override val agentType: AgentType = AgentType.OPENCODE

    // SUCCESS here means the resume command was dispatched to the terminal. The CLI does not
    // expose an observable "agent came back up" signal, so dispatch acceptance is the strongest
    // confirmation available. durationMs is the real elapsed dispatch time, never hardcoded.
    override fun resume(request: AgentResumeRequest): AgentResumeResult {
        if (request.sessionReference.startsWith("detected-")) {
            return AgentResumeResult(AgentResumeStatus.SKIPPED, 0, "SYNTHETIC_ID", "Synthetic session id cannot be resumed")
        }
        val commandExecutor = request.commandExecutor
        var dispatched = true
        val durationMs = measureTimeMillis {
            if (commandExecutor != null) {
                dispatched = commandExecutor(buildResumeCommand(request.sessionReference))
            }
        }
        if (!dispatched) {
            return AgentResumeResult(AgentResumeStatus.FAILED, durationMs, "DISPATCH_FAILED", "Failed to send OpenCode resume command")
        }
        return AgentResumeResult(AgentResumeStatus.SUCCESS, durationMs)
    }

    private fun buildResumeCommand(sessionReference: String): String {
        if (sessionReference.isBlank()) {
            return "opencode --continue"
        }
        return "opencode --session ${escapeArgument(sessionReference)}"
    }

    private fun escapeArgument(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
