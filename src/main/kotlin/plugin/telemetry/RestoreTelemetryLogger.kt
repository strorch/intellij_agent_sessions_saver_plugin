package plugin.telemetry

import com.intellij.openapi.diagnostic.Logger

class RestoreTelemetryLogger {
    private val events = ArrayDeque<RestoreTelemetryEvent>()

    fun log(event: RestoreTelemetryEvent) {
        synchronized(events) {
            events.addLast(event)
            while (events.size > MAX_BUFFERED_EVENTS) {
                events.removeFirst()
            }
        }
        LOG.info(format(event))
    }

    fun allEvents(): List<RestoreTelemetryEvent> {
        synchronized(events) {
            return events.toList()
        }
    }

    private fun format(event: RestoreTelemetryEvent): String {
        val builder = StringBuilder()
        builder.append("restore-telemetry eventType=").append(event.eventType)
        builder.append(" projectScopeId=").append(event.projectScopeId)
        event.terminalTabId?.let { builder.append(" terminalTabId=").append(it) }
        event.status?.let { builder.append(" status=").append(it) }
        event.details?.let { builder.append(" details=").append(it) }
        return builder.toString()
    }

    companion object {
        private val LOG = Logger.getInstance(RestoreTelemetryLogger::class.java)
        private const val MAX_BUFFERED_EVENTS = 500
    }
}
