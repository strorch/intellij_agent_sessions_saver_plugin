package plugin.runtime

import plugin.persistence.model.TerminalSessionSnapshot

enum class StartupAmbiguityState {
    NONE,
    ONE,
    MANY,
}

data class StartupAmbiguityBuckets(
    val exact: List<TerminalSessionSnapshot>,
    val fallback: List<TerminalSessionSnapshot>,
    val ambiguous: List<TerminalSessionSnapshot>,
)

class StartupAmbiguityClassifier {
    fun classify(snapshot: TerminalSessionSnapshot): StartupAmbiguityState {
        val distinctCandidates = snapshot.sessionCandidates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctCandidates.isEmpty()) {
            return StartupAmbiguityState.NONE
        }
        if (distinctCandidates.size == 1) {
            return StartupAmbiguityState.ONE
        }
        return StartupAmbiguityState.MANY
    }

    fun partition(
        snapshots: List<TerminalSessionSnapshot>,
        hasExplicitReference: (TerminalSessionSnapshot) -> Boolean,
        useStartupChooser: Boolean,
    ): StartupAmbiguityBuckets {
        val exact = mutableListOf<TerminalSessionSnapshot>()
        val fallback = mutableListOf<TerminalSessionSnapshot>()
        val ambiguous = mutableListOf<TerminalSessionSnapshot>()
        snapshots.forEach { snapshot ->
            if (hasExplicitReference(snapshot)) {
                exact.add(snapshot)
            } else if (useStartupChooser && classify(snapshot) == StartupAmbiguityState.MANY) {
                ambiguous.add(snapshot)
            } else {
                fallback.add(snapshot)
            }
        }
        return StartupAmbiguityBuckets(exact, fallback, ambiguous)
    }
}
