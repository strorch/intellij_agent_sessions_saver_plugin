package plugin.telemetry

class RestoreTelemetryLogger {
    private val events = mutableListOf<RestoreTelemetryEvent>()

    fun log(event: RestoreTelemetryEvent) {
        events.add(event)
    }

    fun allEvents(): List<RestoreTelemetryEvent> {
        return events.toList()
    }
}
