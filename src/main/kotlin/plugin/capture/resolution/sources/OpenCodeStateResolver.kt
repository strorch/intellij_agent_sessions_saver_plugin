package plugin.capture.resolution.sources

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import plugin.capture.resolution.ResolverContext
import plugin.capture.resolution.SessionReferenceCandidate
import plugin.capture.resolution.SessionReferenceConfidence
import plugin.capture.resolution.SessionReferenceResolver
import plugin.capture.resolution.SessionReferenceSource

class OpenCodeStateResolver(
    private val runner: (List<String>) -> String? = ::runCommand,
) : SessionReferenceResolver {
    override val source: SessionReferenceSource = SessionReferenceSource.PROVIDER_STATE
    private val json = Json { ignoreUnknownKeys = true }

    override fun resolve(context: ResolverContext): List<SessionReferenceCandidate> {
        if (context.agentType.lowercase() != "opencode") {
            return emptyList()
        }
        val raw = runner(listOf("opencode", "session", "list", "--format", "json", "-n", "50")) ?: return emptyList()
        if (raw.isBlank()) {
            return emptyList()
        }
        val targetPath = context.projectBasePath?.let { normalizePath(it) }
        val sessions = parseSessions(raw)
        val filtered = if (targetPath == null) {
            sessions
        } else {
            sessions.filter { it.cwd != null && isInProjectScope(targetPath, it.cwd) }
        }
        val seen = linkedSetOf<String>()
        return filtered.mapNotNull { if (seen.add(it.id)) it else null }
            .take(6)
            .map { SessionReferenceCandidate(it.id, source, SessionReferenceConfidence.HIGH) }
    }

    private fun parseSessions(raw: String): List<OpenCodeSessionMeta> {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        return when (element) {
            is JsonArray -> parseSessionArray(element)
            is JsonObject -> parseSessionObjectRoot(element)
            else -> emptyList()
        }
    }

    private fun parseSessionArray(array: JsonArray): List<OpenCodeSessionMeta> {
        return array.mapNotNull { parseSessionObject(it) }
    }

    private fun parseSessionObjectRoot(obj: JsonObject): List<OpenCodeSessionMeta> {
        val arrayKeys = listOf("sessions", "items", "data", "results")
        arrayKeys.forEach { key ->
            val nested = obj[key] as? JsonArray
            if (nested != null) {
                return parseSessionArray(nested)
            }
        }
        val single = parseSessionObject(obj)
        return if (single == null) emptyList() else listOf(single)
    }

    private fun parseSessionObject(element: JsonElement): OpenCodeSessionMeta? {
        val obj = element as? JsonObject ?: return null
        val id = stringField(obj, "id", "sessionId", "sessionID", "session")
        val cwdRaw = stringField(obj, "cwd", "path", "workspacePath", "dir")
            ?: nestedStringField(obj, listOf("workspace", "project"), "cwd", "path", "dir")
        if (id.isNullOrBlank()) {
            return null
        }
        return OpenCodeSessionMeta(id = id, cwd = cwdRaw?.let { normalizePath(it) })
    }

    private fun stringField(obj: JsonObject, vararg keys: String): String? {
        keys.forEach { key ->
            val value = obj[key]?.toString()?.trim()?.trim('"')
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun nestedStringField(obj: JsonObject, containers: List<String>, vararg keys: String): String? {
        containers.forEach { container ->
            val nested = obj[container] as? JsonObject ?: return@forEach
            val nestedValue = stringField(nested, *keys)
            if (!nestedValue.isNullOrBlank()) {
                return nestedValue
            }
        }
        return null
    }

    private fun normalizePath(path: String): String {
        return java.io.File(path).absolutePath.replace('\\', '/').trimEnd('/')
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

private data class OpenCodeSessionMeta(
    val id: String,
    val cwd: String?,
)

private fun runCommand(command: List<String>): String? {
    val process = runCatching { ProcessBuilder(command).redirectErrorStream(true).start() }.getOrNull() ?: return null
    val output = runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
    val completed = runCatching { process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
    if (!completed || process.exitValue() != 0) {
        process.destroyForcibly()
        return null
    }
    return output
}
