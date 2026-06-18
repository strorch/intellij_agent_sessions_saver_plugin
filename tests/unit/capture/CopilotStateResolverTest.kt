package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.capture.resolution.ProcessMetadata
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.sources.CopilotStateResolver
import java.io.File

class CopilotStateResolverTest {
    @Test
    fun `maps copilot workspace state by cwd and picks latest timestamp first`() {
        val home = createTempDir(prefix = "copilot-home-")
        val stateRoot = File(home, ".copilot/session-state")
        writeWorkspaceState(stateRoot, "a", "/repo/project-a", "cp-old", 1000)
        writeWorkspaceState(stateRoot, "b", "/repo/project-a", "cp-new", 2000)
        writeWorkspaceState(stateRoot, "c", "/repo/project-b", "cp-other", 9999)
        val resolver = CopilotStateResolver(home)

        val candidates = resolver.resolve(context("/repo/project-a"))

        assertEquals(2, candidates.size)
        assertEquals("cp-new", candidates.first().sessionReference)
        assertEquals("cp-old", candidates.last().sessionReference)
        home.deleteRecursively()
    }

    private fun writeWorkspaceState(root: File, folder: String, workspacePath: String, sessionId: String, updatedAt: Long) {
        val dir = File(root, folder)
        dir.mkdirs()
        File(dir, "workspace.yaml").writeText(
            """
            workspacePath: $workspacePath
            sessionId: $sessionId
            updatedAt: $updatedAt
            """.trimIndent(),
        )
    }

    private fun context(projectPath: String): ResolverContext {
        return ResolverContext(
            agentType = "copilot",
            projectBasePath = projectPath,
            processMetadata = ProcessMetadata(null, ""),
            processCommandLine = "",
            typedCommand = "",
            shellCommand = "",
            terminalText = "",
        )
    }
}
