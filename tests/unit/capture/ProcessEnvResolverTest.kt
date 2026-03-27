package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.sources.ProcessEnvResolver
import java.io.File

class ProcessEnvResolverTest {
    @Test
    fun `parses session-like variables from proc environ`() {
        val root = createTempDir(prefix = "proc-root-")
        val pidDir = File(root, "1200")
        pidDir.mkdirs()
        File(pidDir, "environ").writeBytes("CODEX_SESSION_ID=sess-001\u0000PATH=/usr/bin\u0000".toByteArray())
        val resolver = ProcessEnvResolver(root)

        val candidates = resolver.resolve(context(pid = 1200L))

        assertEquals(1, candidates.size)
        assertEquals("sess-001", candidates.first().sessionReference)
        root.deleteRecursively()
    }

    @Test
    fun `returns empty list when proc environ is missing`() {
        val root = createTempDir(prefix = "proc-root-missing-")
        val resolver = ProcessEnvResolver(root)

        val candidates = resolver.resolve(context(pid = 4040L))

        assertEquals(0, candidates.size)
        root.deleteRecursively()
    }

    @Test
    fun `accepts codex uuid values from process env`() {
        val root = createTempDir(prefix = "proc-root-uuid-")
        val pidDir = File(root, "2200")
        pidDir.mkdirs()
        File(pidDir, "environ").writeBytes("CODEX_SESSION_ID=d20de65d-40ef-4145-9f5f-85a8d5faad5c\u0000".toByteArray())
        val resolver = ProcessEnvResolver(root)

        val candidates = resolver.resolve(context(pid = 2200L))

        assertEquals(1, candidates.size)
        assertEquals("d20de65d-40ef-4145-9f5f-85a8d5faad5c", candidates.first().sessionReference)
        root.deleteRecursively()
    }

    private fun context(pid: Long): ResolverContext {
        return ResolverContext(
            agentType = "codex",
            projectBasePath = null,
            processMetadata = ProcessMetadata(pid, "codex resume sess-001"),
            processCommandLine = "codex resume sess-001",
            typedCommand = "",
            shellCommand = "",
            terminalText = "",
        )
    }
}
