package plugin.capture.resolution.sources

import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class TerminalTextResolver : SessionReferenceResolver {
    override val source: SessionReferenceSource = SessionReferenceSource.TERMINAL_TEXT
    private val tokenPattern = "(?=[A-Za-z0-9._:-]{8,})(?=[A-Za-z0-9._:-]*[A-Za-z])[A-Za-z0-9._:-]+"

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        val text = context.terminalText
        if (text.isBlank()) {
            return emptyList()
        }
        val patterns = patternsFor(context.agentType)
        val candidates = mutableListOf<SessionReferenceCandidate>()
        patterns.forEach { regex ->
            regex.findAll(text).forEach { match ->
                val value = match.groupValues.getOrNull(1).orEmpty()
                if (value.isNotBlank()) {
                    candidates.add(SessionReferenceCandidate(value, source, SessionReferenceConfidence.LOW))
                }
            }
        }
        return candidates
    }

    private fun patternsFor(agentTypeRaw: String): List<Regex> {
        return when (agentTypeRaw.lowercase()) {
            "codex" -> listOf(Regex("(?i)\\bcodex\\s+resume\\s+($tokenPattern)"))
            "claude" -> listOf(Regex("(?i)\\bclaude\\b[^\\n\\r]*--resume(?:=|\\s+)($tokenPattern)"))
            "opencode" -> listOf(Regex("(?i)\\bopencode\\b[^\\n\\r]*--session(?:=|\\s+)($tokenPattern)"))
            "copilot" -> listOf(Regex("(?i)\\b(?:gh\\s+copilot|copilot)\\b[^\\n\\r]*--resume(?:=|\\s+)($tokenPattern)"))
            else -> emptyList()
        }
    }
}
