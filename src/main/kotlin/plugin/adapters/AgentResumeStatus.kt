package plugin.adapters

enum class AgentType {
    CODEX,
    CLAUDE,
    OPENCODE,
    COPILOT,
    UNKNOWN,
}

enum class AgentResumeStatus {
    SUCCESS,
    TIMEOUT,
    FAILED,
    SKIPPED,
    UNSUPPORTED,
}

data class AgentResumeRequest(
    val projectScopeId: String,
    val terminalTabId: String,
    val sessionReference: String,
    val timeoutSeconds: Int,
    val commandExecutor: ((String) -> Boolean)? = null,
)

data class AgentResumeResult(
    val status: AgentResumeStatus,
    val durationMs: Long,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)
