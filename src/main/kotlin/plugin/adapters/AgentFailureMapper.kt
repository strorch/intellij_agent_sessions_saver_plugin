package plugin.adapters

class AgentFailureMapper {
    fun normalizeFailure(code: String?, message: String?): Pair<String, String> {
        val normalizedCode = when (code) {
            "EXPIRED" -> "SESSION_EXPIRED"
            "MISSING" -> "SESSION_NOT_FOUND"
            "UNSUPPORTED" -> "UNSUPPORTED_AGENT"
            else -> "RESUME_FAILED"
        }
        val normalizedMessage = message ?: "Agent session resume failed"
        return normalizedCode to normalizedMessage
    }
}
