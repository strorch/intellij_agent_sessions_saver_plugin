package plugin.capture

private const val UNKNOWN_AGENT = "unknown"
private const val SESSION_TOKEN_PATTERN = "(?=[A-Za-z0-9._:-]{8,})(?=[A-Za-z0-9._:-]*[A-Za-z])[A-Za-z0-9._:-]+"

internal data class DetectionSource(
    val processCommandLine: String,
    val typedCommand: String,
    val shellCommand: String,
    val terminalText: String,
) {
    fun fallbackText(): String {
        return listOf(typedCommand, shellCommand, terminalText).joinToString("\n")
    }
}

internal data class Detection(
    val agentType: String,
    val sessionReference: String?,
    val hasActiveSession: Boolean,
)

internal object AgentSessionDetector {
    private val numericVersionPattern = Regex("^[.-]?\\d+(?:\\.\\d+){1,4}$")
    private val copilotPattern = Regex("(?i)(?:^|[\\s/>%$#;|&])(?:gh\\s+copilot|github-copilot|copilot)\\b")
    private val opencodePattern = Regex("(?i)(?:^|[\\s/>%$#;|&])opencode\\b")
    private val codexPattern = Regex("(?i)(?:^|[\\s/>%$#;|&])codex\\b")
    private val claudePattern = Regex("(?i)(?:^|[\\s/>%$#;|&])claude\\b")

    fun detect(source: DetectionSource): Detection {
        val processDetection = detectFromProcessCommand(source.processCommandLine)
        if (processDetection.agentType != UNKNOWN_AGENT) {
            return processDetection
        }
        return detectFromFallbackText(source.fallbackText())
    }

    private fun detectFromProcessCommand(processCommandLine: String): Detection {
        if (processCommandLine.isBlank()) {
            return Detection(UNKNOWN_AGENT, null, false)
        }
        val agentType = detectAgentType(processCommandLine)
        if (agentType == UNKNOWN_AGENT) {
            return Detection(UNKNOWN_AGENT, null, false)
        }
        val sessionReference = findSessionReference(agentType, processCommandLine)
        return Detection(agentType, sessionReference, true)
    }

    private fun detectFromFallbackText(text: String): Detection {
        if (text.isBlank()) {
            return Detection(UNKNOWN_AGENT, null, false)
        }
        val agentType = detectAgentType(text)
        if (agentType == UNKNOWN_AGENT) {
            return Detection(UNKNOWN_AGENT, null, false)
        }
        return Detection(agentType, null, true)
    }

    private fun detectAgentType(source: String): String {
        val priorities = listOf(
            AgentPattern("copilot", copilotPattern),
            AgentPattern("opencode", opencodePattern),
            AgentPattern("codex", codexPattern),
            AgentPattern("claude", claudePattern),
        )
        priorities.forEach { pattern ->
            if (pattern.regex.containsMatchIn(source)) {
                return pattern.agentType
            }
        }
        return UNKNOWN_AGENT
    }

    private fun findSessionReference(agentType: String, vararg sources: String): String? {
        val patterns = sessionPatterns(agentType)
        sources.forEach { source ->
            if (source.isBlank()) {
                return@forEach
            }
            patterns.forEach { pattern ->
                val matched = findLastGroupMatch(pattern, source)
                val normalized = normalizeSessionReference(matched)
                if (!normalized.isNullOrBlank()) {
                    return normalized
                }
            }
        }
        return null
    }

    private fun normalizeSessionReference(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val normalized = value.trim().trim('"', '\'')
        if (normalized.isBlank()) {
            return null
        }
        if (normalized.startsWith("detected-")) {
            return null
        }
        if (numericVersionPattern.matches(normalized)) {
            return null
        }
        return normalized
    }

    private fun findLastGroupMatch(pattern: Regex, source: String): String? {
        var matchedValue: String? = null
        pattern.findAll(source).forEach { match ->
            matchedValue = match.groupValues.getOrNull(1)
        }
        return matchedValue
    }

    private fun sessionPatterns(agentType: String): List<Regex> {
        return when (agentType) {
            "codex" -> listOf(
                Regex("(?i)\\bcodex\\s+resume\\s+($SESSION_TOKEN_PATTERN)"),
            )
            "claude" -> listOf(
                Regex("(?i)\\bclaude\\b[^\\n\\r]*--resume(?:=|\\s+)($SESSION_TOKEN_PATTERN)"),
                Regex("(?i)\\bclaude\\b[^\\n\\r]*--session(?:-id)?(?:=|\\s+)($SESSION_TOKEN_PATTERN)"),
            )
            "opencode" -> listOf(
                Regex("(?i)\\bopencode\\b[^\\n\\r]*--session(?:=|\\s+)($SESSION_TOKEN_PATTERN)"),
                Regex("(?i)\\bopencode\\b[^\\n\\r]*\\s-s\\s+($SESSION_TOKEN_PATTERN)"),
            )
            "copilot" -> listOf(
                Regex("(?i)\\b(?:gh\\s+copilot|github-copilot|copilot)\\b[^\\n\\r]*--resume(?:=|\\s+)($SESSION_TOKEN_PATTERN)"),
            )
            else -> emptyList()
        }
    }
}

private data class AgentPattern(
    val agentType: String,
    val regex: Regex,
)
