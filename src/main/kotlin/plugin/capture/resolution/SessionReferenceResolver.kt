package plugin.capture.resolution

interface SessionReferenceResolver {
    val source: SessionReferenceSource

    fun resolve(context: ResolverContext): List<SessionReferenceCandidate>
}
