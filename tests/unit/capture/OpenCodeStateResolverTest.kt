package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.sources.OpenCodeStateResolver

class OpenCodeStateResolverTest {
    @Test
    fun `resolves opencode session ids from json list filtered by cwd`() {
        val json = """
            [
              {"id":"ses_a","cwd":"/repo/app"},
              {"sessionId":"ses_b","path":"/repo/app"},
              {"id":"ses_c","cwd":"/repo/other"}
            ]
        """.trimIndent()
        val resolver = OpenCodeStateResolver { json }

        val candidates = resolver.resolve(context("/repo/app"))

        assertEquals(2, candidates.size)
        assertEquals("ses_a", candidates[0].sessionReference)
        assertEquals("ses_b", candidates[1].sessionReference)
    }

    @Test
    fun `resolves opencode session ids from wrapped payload`() {
        val json = """
            {
              "sessions": [
                {"id":"ses_wrapped","workspace":{"path":"/repo/app/module"}},
                {"id":"ses_other","workspace":{"path":"/repo/other"}}
              ]
            }
        """.trimIndent()
        val resolver = OpenCodeStateResolver { json }

        val candidates = resolver.resolve(context("/repo/app"))

        assertEquals(1, candidates.size)
        assertEquals("ses_wrapped", candidates[0].sessionReference)
    }

    private fun context(projectPath: String): ResolverContext {
        return ResolverContext(
            agentType = "opencode",
            projectBasePath = projectPath,
            processMetadata = ProcessMetadata(null, ""),
            processCommandLine = "",
            typedCommand = "",
            shellCommand = "",
            terminalText = "",
        )
    }
}
