package plugin.restore.ui

import plugin.persistence.model.RestoreAttemptResult

data class RestoreSummaryCounters(
    val attempted: Int = 0,
    val exactRestored: Int = 0,
    val chooserResolved: Int = 0,
    val fallbackRestored: Int = 0,
    val skipped: Int = 0,
)

class RestoreSummaryNotifier {
    private var latestSummary: List<RestoreAttemptResult> = emptyList()
    private var latestCounters: RestoreSummaryCounters = RestoreSummaryCounters()

    fun publish(results: List<RestoreAttemptResult>, counters: RestoreSummaryCounters = latestCounters) {
        latestSummary = results
        latestCounters = counters
    }

    fun latest(): List<RestoreAttemptResult> {
        return latestSummary
    }

    fun latestCounters(): RestoreSummaryCounters {
        return latestCounters
    }
}
