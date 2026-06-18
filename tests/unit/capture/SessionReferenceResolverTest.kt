package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverChain
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.ResolverRegistry
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource
import plugin.capture.resolution.sources.CommandLineResolver

class SessionReferenceResolverTest {
    @Test
    fun `command line resolver extracts codex resume id`() {
        val resolver = CommandLineResolver()
        val candidates = resolver.resolve(context(agentType = "codex", processCommand = "codex resume abcdefgh-1234"))

        assertTrue(candidates.any { it.sessionReference == "abcdefgh-1234" })
        assertTrue(candidates.all { it.source == SessionReferenceSource.COMMAND_LINE })
    }

    @Test
    fun `resolver chain rejects version-like ids`() {
        val resolver = FixedResolver(listOf(SessionReferenceCandidate(".3.2", SessionReferenceSource.COMMAND_LINE, SessionReferenceConfidence.HIGH)))
        val chain = ResolverChain(ResolverRegistry(listOf(resolver)))

        val resolved = chain.resolve(context())

        assertNull(resolved.sessionReference)
        assertEquals(0, resolved.candidates.size)
    }

    @Test
    fun `resolver chain rejects synthetic ids`() {
        val resolver = FixedResolver(listOf(SessionReferenceCandidate("detected-codex-1", SessionReferenceSource.TERMINAL_TEXT, SessionReferenceConfidence.LOW)))
        val chain = ResolverChain(ResolverRegistry(listOf(resolver)))

        val resolved = chain.resolve(context())

        assertNull(resolved.sessionReference)
        assertEquals(0, resolved.candidates.size)
    }

    @Test
    fun `resolver chain keeps provider-state candidates without promoting ambiguous primary`() {
        val resolver = FixedResolver(
            listOf(
                SessionReferenceCandidate("id-a", SessionReferenceSource.PROVIDER_STATE, SessionReferenceConfidence.HIGH),
                SessionReferenceCandidate("id-b", SessionReferenceSource.PROVIDER_STATE, SessionReferenceConfidence.HIGH),
            ),
        )
        val chain = ResolverChain(ResolverRegistry(listOf(resolver)))

        val resolved = chain.resolve(context())

        assertNull(resolved.sessionReference)
        assertEquals(2, resolved.candidates.size)
        assertEquals(SessionReferenceSource.PROVIDER_STATE, resolved.source)
    }

    @Test
    fun `resolver chain promotes single provider-state candidate as primary`() {
        val resolver = FixedResolver(
            listOf(
                SessionReferenceCandidate("id-a", SessionReferenceSource.PROVIDER_STATE, SessionReferenceConfidence.HIGH),
            ),
        )
        val chain = ResolverChain(ResolverRegistry(listOf(resolver)))

        val resolved = chain.resolve(context())

        assertEquals("id-a", resolved.sessionReference)
        assertEquals(1, resolved.candidates.size)
    }

    @Test
    fun `resolver chain promotes single process-env candidate as primary`() {
        val resolver = FixedResolver(
            listOf(
                SessionReferenceCandidate(
                    "d20de65d-40ef-4145-9f5f-85a8d5faad5c",
                    SessionReferenceSource.PROCESS_ENV,
                    SessionReferenceConfidence.HIGH,
                ),
            ),
        )
        val chain = ResolverChain(ResolverRegistry(listOf(resolver)))

        val resolved = chain.resolve(context())

        assertEquals("d20de65d-40ef-4145-9f5f-85a8d5faad5c", resolved.sessionReference)
        assertEquals(SessionReferenceSource.PROCESS_ENV, resolved.source)
    }

    private fun context(
        agentType: String = "codex",
        processCommand: String = "",
    ): ResolverContext {
        return ResolverContext(
            agentType = agentType,
            projectBasePath = null,
            processMetadata = ProcessMetadata(1, processCommand),
            processCommandLine = processCommand,
            typedCommand = "",
            shellCommand = processCommand,
            terminalText = "",
        )
    }
}

private class FixedResolver(
    private val results: List<SessionReferenceCandidate>,
) : SessionReferenceResolver {
    override val source: SessionReferenceSource = results.firstOrNull()?.source ?: SessionReferenceSource.UNKNOWN

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        return results
    }
}
