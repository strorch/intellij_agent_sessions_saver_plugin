package plugin.capture.resolution

class ResolverChain(
    private val registry: ResolverRegistry = ResolverRegistry(),
) {
    private val numericVersionPattern = Regex("^[.-]?\\d+(?:\\.\\d+){1,4}$")
    private val maxCandidates = 6

    fun resolve(context: ResolverContext): ResolvedSessionReference {
        val aggregated = mutableListOf<SessionReferenceCandidate>()
        registry.ordered(context.agentType).forEach { resolver ->
            val candidates = resolver.resolve(context)
                .mapNotNull { normalizeCandidate(it) }
            aggregated.addAll(candidates)
        }
        val unique = uniqueCandidates(aggregated)
        val exactCandidates = unique.filter { isTrustedExactCandidate(it) }.take(maxCandidates)
        val primary = selectPrimaryCandidate(exactCandidates)
        val source = primary?.source ?: dominantSource(exactCandidates)
        val confidence = primary?.confidence ?: dominantConfidence(exactCandidates)
        return ResolvedSessionReference(
            sessionReference = primary?.sessionReference,
            source = source,
            confidence = confidence,
            candidates = exactCandidates,
        )
    }

    private fun normalizeCandidate(candidate: SessionReferenceCandidate): SessionReferenceCandidate? {
        val normalized = candidate.sessionReference.trim().trim('"', '\'')
        if (normalized.isBlank() || normalized.startsWith("detected-")) {
            return null
        }
        if (numericVersionPattern.matches(normalized)) {
            return null
        }
        return candidate.copy(sessionReference = normalized)
    }

    private fun uniqueCandidates(candidates: List<SessionReferenceCandidate>): List<SessionReferenceCandidate> {
        val seen = mutableSetOf<String>()
        return candidates.filter { seen.add(it.sessionReference) }
    }

    private fun isTrustedExactCandidate(candidate: SessionReferenceCandidate): Boolean {
        if (candidate.confidence != SessionReferenceConfidence.HIGH) {
            return false
        }
        return candidate.source == SessionReferenceSource.COMMAND_LINE ||
            candidate.source == SessionReferenceSource.PROCESS_ENV ||
            candidate.source == SessionReferenceSource.PROVIDER_STATE
    }

    private fun selectPrimaryCandidate(candidates: List<SessionReferenceCandidate>): SessionReferenceCandidate? {
        val commandLineCandidate = candidates.firstOrNull { it.source == SessionReferenceSource.COMMAND_LINE }
        if (commandLineCandidate != null) {
            return commandLineCandidate
        }
        val envCandidates = candidates.filter { it.source == SessionReferenceSource.PROCESS_ENV }
        if (envCandidates.size == 1) {
            return envCandidates.first()
        }
        val providerCandidates = candidates.filter { it.source == SessionReferenceSource.PROVIDER_STATE }
        if (providerCandidates.size == 1) {
            return providerCandidates.first()
        }
        return null
    }

    private fun dominantSource(candidates: List<SessionReferenceCandidate>): SessionReferenceSource {
        if (candidates.isEmpty()) {
            return SessionReferenceSource.UNKNOWN
        }
        return candidates.first().source
    }

    private fun dominantConfidence(candidates: List<SessionReferenceCandidate>): SessionReferenceConfidence {
        if (candidates.isEmpty()) {
            return SessionReferenceConfidence.NONE
        }
        return candidates.first().confidence
    }
}
