package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.sources.CodexStateResolver
import java.io.File

class CodexStateResolverTest {
    @Test
    fun `resolves codex session ids by project cwd from rollout meta`() {
        val home = createTempDir(prefix = "codex-home-")
        val sessionDir = File(home, ".codex/sessions/2026/03/27")
        sessionDir.mkdirs()
        val first = File(sessionDir, "rollout-1.jsonl")
        first.writeText("""{"type":"session_meta","payload":{"id":"id-1","cwd":"/repo/app"}}""")
        first.setLastModified(1000L)
        val second = File(sessionDir, "rollout-2.jsonl")
        second.writeText("""{"type":"session_meta","payload":{"id":"id-2","cwd":"/repo/app"}}""")
        second.setLastModified(2000L)
        val resolver = CodexStateResolver(home)

        val candidates = resolver.resolve(context("/repo/app"))

        assertEquals(2, candidates.size)
        assertEquals("id-2", candidates.first().sessionReference)
        assertEquals("id-1", candidates.last().sessionReference)
        home.deleteRecursively()
    }

    @Test
    fun `accepts session_meta line with spacing and nested project path`() {
        val home = createTempDir(prefix = "codex-home-spacing-")
        val sessionDir = File(home, ".codex/sessions/2026/03/27")
        sessionDir.mkdirs()
        val file = File(sessionDir, "rollout-3.jsonl")
        file.writeText("""{ "type" : "session_meta", "payload": { "id" : "id-spaced", "cwd" : "/repo/app/module" } }""")
        val resolver = CodexStateResolver(home)

        val candidates = resolver.resolve(context("/repo/app"))

        assertEquals(1, candidates.size)
        assertEquals("id-spaced", candidates.first().sessionReference)
        home.deleteRecursively()
    }

    private fun context(projectPath: String): ResolverContext {
        return ResolverContext(
            agentType = "codex",
            projectBasePath = projectPath,
            processMetadata = ProcessMetadata(null, ""),
            processCommandLine = "",
            typedCommand = "",
            shellCommand = "",
            terminalText = "",
        )
    }
}
