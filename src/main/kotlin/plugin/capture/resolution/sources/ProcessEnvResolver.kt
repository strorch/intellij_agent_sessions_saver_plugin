package plugin.capture.resolution.sources

import java.io.File
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class ProcessEnvResolver(
    private val procRoot: File = File("/proc"),
) : SessionReferenceResolver {
    override val source: SessionReferenceSource = SessionReferenceSource.PROCESS_ENV
    private val valuePattern = Regex("(?=[A-Za-z0-9._:-]{8,})(?=[A-Za-z0-9._:-]*[A-Za-z])[A-Za-z0-9._:-]+")

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        val pid = context.processMetadata.processId ?: return emptyList()
        val environFile = File(File(procRoot, pid.toString()), "environ")
        if (!environFile.exists() || !environFile.canRead()) {
            return emptyList()
        }
        val raw = runCatching { environFile.readBytes() }.getOrNull() ?: return emptyList()
        val entries = String(raw).split('\u0000')
        val candidates = mutableListOf<SessionReferenceCandidate>()
        entries.forEach { entry ->
            val index = entry.indexOf('=')
            if (index < 1) {
                return@forEach
            }
            val key = entry.substring(0, index).trim()
            val value = entry.substring(index + 1).trim()
            if (!isAllowedEnvKey(context.agentType, key)) {
                return@forEach
            }
            if (!valuePattern.matches(value)) {
                return@forEach
            }
            candidates.add(SessionReferenceCandidate(value, source, SessionReferenceConfidence.HIGH))
        }
        return candidates
    }

    private fun isAllowedEnvKey(agentTypeRaw: String, key: String): Boolean {
        val upper = key.uppercase()
        val allowedPrefixes = when (agentTypeRaw.lowercase()) {
            "codex" -> listOf("CODEX_", "OPENAI_")
            "claude" -> listOf("CLAUDE_", "ANTHROPIC_")
            "opencode" -> listOf("OPENCODE_")
            "copilot" -> listOf("COPILOT_", "GITHUB_COPILOT_", "GH_COPILOT_")
            else -> emptyList()
        }
        val hasProviderPrefix = allowedPrefixes.any { upper.startsWith(it) }
        val hasSessionToken = upper.contains("SESSION") || upper.contains("THREAD")
        val hasIdToken = upper.contains("ID") || upper.contains("REF")
        return hasProviderPrefix && hasSessionToken && hasIdToken
    }
}
