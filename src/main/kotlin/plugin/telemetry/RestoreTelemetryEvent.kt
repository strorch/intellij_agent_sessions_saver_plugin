package plugin.telemetry

data class RestoreTelemetryEvent(
    val eventType: String,
    val projectScopeId: String,
    val terminalTabId: String? = null,
    val status: String? = null,
    val details: String? = null,
)
