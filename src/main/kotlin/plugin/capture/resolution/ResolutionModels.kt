package plugin.capture.resolution

enum class SessionReferenceSource {
    COMMAND_LINE,
    PROCESS_ENV,
    TERMINAL_TEXT,
    PROVIDER_STATE,
    UNKNOWN,
}

enum class SessionReferenceConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

data class SessionReferenceCandidate(
    val sessionReference: String,
    val source: SessionReferenceSource,
    val confidence: SessionReferenceConfidence,
)

data class ProcessMetadata(
    val processId: Long?,
    val processCommand: String,
)

data class ResolverContext(
    val agentType: String,
    val projectBasePath: String?,
    val processMetadata: ProcessMetadata,
    val processCommandLine: String,
    val typedCommand: String,
    val shellCommand: String,
    val terminalText: String,
)

data class ResolvedSessionReference(
    val sessionReference: String?,
    val source: SessionReferenceSource,
    val confidence: SessionReferenceConfidence,
    val candidates: List<SessionReferenceCandidate>,
)
