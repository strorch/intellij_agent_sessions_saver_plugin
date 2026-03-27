package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.ResolverRegistry
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class ResolverRegistryTest {
    @Test
    fun `keeps all resolvers for duplicated source`() {
        val firstProvider = NamedResolver("first-provider", SessionReferenceSource.PROVIDER_STATE)
        val secondProvider = NamedResolver("second-provider", SessionReferenceSource.PROVIDER_STATE)
        val registry = ResolverRegistry(
            listOf(
                NamedResolver("command", SessionReferenceSource.COMMAND_LINE),
                firstProvider,
                secondProvider,
            ),
        )

        val ordered = registry.ordered("codex")

        assertEquals(listOf("command", "first-provider", "second-provider"), ordered.map { (it as NamedResolver).name })
    }
}

private class NamedResolver(
    val name: String,
    override val source: SessionReferenceSource,
) : SessionReferenceResolver {
    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        return listOf(SessionReferenceCandidate(name, source, SessionReferenceConfidence.HIGH))
    }
}
