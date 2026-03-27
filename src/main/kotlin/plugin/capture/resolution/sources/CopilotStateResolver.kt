package plugin.capture.resolution.sources

import java.io.File
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class CopilotStateResolver(
    private val homeDir: File = File(System.getProperty("user.home")),
) : SessionReferenceResolver {
    override val source: SessionReferenceSource = SessionReferenceSource.PROVIDER_STATE

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        if (context.agentType.lowercase() != "copilot") {
            return emptyList()
        }
        val stateDir = File(homeDir, ".copilot/session-state")
        if (!stateDir.exists() || !stateDir.isDirectory) {
            return emptyList()
        }
        val targetPath = context.projectBasePath?.let { normalizePath(it) }
        val states = stateDir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && it.name == "workspace.yaml" }
            .mapNotNull { parseState(it) }
            .sortedByDescending { it.updatedAt }
            .toList()
        val filtered = if (targetPath == null) states else states.filter { normalizePath(it.workspacePath) == targetPath }
        return filtered.map { state ->
            val confidence = if (targetPath != null) SessionReferenceConfidence.HIGH else SessionReferenceConfidence.MEDIUM
            SessionReferenceCandidate(state.sessionReference, source, confidence)
        }
    }

    private fun parseState(file: File): CopilotWorkspaceState? {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        val sessionId = readValue(lines, "sessionId") ?: readValue(lines, "session_id") ?: return null
        val workspace = readValue(lines, "workspacePath") ?: readValue(lines, "cwd") ?: ""
        val updatedRaw = readValue(lines, "updatedAt") ?: readValue(lines, "updated_at") ?: "0"
        return CopilotWorkspaceState(workspace, sessionId, updatedRaw.toLongOrNull() ?: 0L)
    }

    private fun readValue(lines: List<String>, key: String): String? {
        val regex = Regex("^\\s*$key\\s*:\\s*(.+?)\\s*$")
        lines.forEach { line ->
            val match = regex.find(line) ?: return@forEach
            return match.groupValues[1].trim().trim('"', '\'')
        }
        return null
    }

    private fun normalizePath(path: String): String {
        return File(path).absolutePath.replace('\\', '/').trimEnd('/')
    }
}

private data class CopilotWorkspaceState(
    val workspacePath: String,
    val sessionReference: String,
    val updatedAt: Long,
)
