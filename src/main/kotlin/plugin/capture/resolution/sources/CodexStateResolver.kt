package plugin.capture.resolution.sources

import java.io.File
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class CodexStateResolver(
    private val homeDir: File = File(System.getProperty("user.home")),
) : SessionReferenceResolver {
    override val source: SessionReferenceSource = SessionReferenceSource.PROVIDER_STATE
    private val metaTypePattern = Regex("\"type\"\\s*:\\s*\"session_meta\"")
    private val idPattern = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
    private val cwdPattern = Regex("\"cwd\"\\s*:\\s*\"([^\"]+)\"")

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        if (context.agentType.lowercase() != "codex") {
            return emptyList()
        }
        val sessionsRoot = File(homeDir, ".codex/sessions")
        if (!sessionsRoot.exists() || !sessionsRoot.isDirectory) {
            return emptyList()
        }
        val targetPath = context.projectBasePath?.let { normalizePath(it) }
        val files = sessionsRoot.walkTopDown()
            .maxDepth(5)
            .filter { it.isFile && it.name.startsWith("rollout-") && it.name.endsWith(".jsonl") }
            .sortedByDescending { it.lastModified() }
            .take(120)
            .toList()
        val states = files.mapNotNull { parseMeta(it) }
        val filtered = if (targetPath == null) {
            states
        } else {
            states.filter { isInProjectScope(targetPath, it.cwd) }
        }
        val uniqueIds = linkedSetOf<String>()
        return filtered
            .sortedByDescending { it.updatedAt }
            .mapNotNull { if (uniqueIds.add(it.id)) it else null }
            .take(6)
            .map { SessionReferenceCandidate(it.id, source, SessionReferenceConfidence.HIGH) }
    }

    private fun parseMeta(file: File): CodexSessionMeta? {
        val lines = runCatching { file.useLines { it.take(20).toList() } }.getOrNull() ?: return null
        for (line in lines) {
            if (!metaTypePattern.containsMatchIn(line)) {
                continue
            }
            val id = idPattern.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val cwdRaw = cwdPattern.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (id.isBlank() || cwdRaw.isBlank()) {
                continue
            }
            return CodexSessionMeta(id = id, cwd = normalizePath(cwdRaw), updatedAt = file.lastModified())
        }
        return null
    }

    private fun normalizePath(path: String): String {
        return File(path).absolutePath.replace('\\', '/').trimEnd('/')
    }

    private fun isInProjectScope(projectPath: String, sessionPath: String): Boolean {
        val project = projectPath.trimEnd('/')
        val session = sessionPath.trimEnd('/')
        if (project == session) {
            return true
        }
        return session.startsWith("$project/") || project.startsWith("$session/")
    }
}

private data class CodexSessionMeta(
    val id: String,
    val cwd: String,
    val updatedAt: Long,
)
