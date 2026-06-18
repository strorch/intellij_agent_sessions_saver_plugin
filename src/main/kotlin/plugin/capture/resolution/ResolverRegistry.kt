package plugin.capture.resolution

import plugin.capture.resolution.sources.CommandLineResolver
import plugin.capture.resolution.sources.CodexStateResolver
import plugin.capture.resolution.sources.CopilotStateResolver
import plugin.capture.resolution.sources.OpenCodeStateResolver
import plugin.capture.resolution.sources.ProcessEnvResolver
import plugin.capture.resolution.sources.TerminalTextResolver

class ResolverRegistry(
    private val resolvers: List<SessionReferenceResolver> = listOf(
        CommandLineResolver(),
        ProcessEnvResolver(),
        TerminalTextResolver(),
        CodexStateResolver(),
        OpenCodeStateResolver(),
        CopilotStateResolver(),
    ),
) {
    private val defaultPriority = listOf(
        SessionReferenceSource.COMMAND_LINE,
        SessionReferenceSource.PROCESS_ENV,
        SessionReferenceSource.TERMINAL_TEXT,
        SessionReferenceSource.PROVIDER_STATE,
    )

    private val priorityByAgent = mapOf(
        "copilot" to defaultPriority,
        "codex" to defaultPriority,
        "claude" to defaultPriority,
        "opencode" to defaultPriority,
        "unknown" to defaultPriority,
    )

    fun ordered(agentTypeRaw: String): List<SessionReferenceResolver> {
        val agentType = agentTypeRaw.lowercase()
        val priority = priorityByAgent[agentType] ?: defaultPriority
        return priority.flatMap { source ->
            resolvers.filter { it.source == source }
        }
    }
}
