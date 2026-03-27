package tests.unit.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.restore.ui.StartupSessionChooser
import plugin.restore.ui.StartupSessionChooserRequest
import plugin.restore.ui.StartupSessionChooserResult
import plugin.runtime.StartupAmbiguityClassifier
import plugin.runtime.StartupAmbiguityState
import java.lang.reflect.Proxy

class StartupAmbiguityResolutionTest {
    @Test
    fun `classifies candidate counts as none one many`() {
        val classifier = StartupAmbiguityClassifier()

        assertEquals(StartupAmbiguityState.NONE, classifier.classify(RestoreFixtureFactory.snapshot(sessionReference = null)))
        assertEquals(StartupAmbiguityState.ONE, classifier.classify(RestoreFixtureFactory.snapshot(sessionCandidates = listOf("s1"))))
        assertEquals(StartupAmbiguityState.MANY, classifier.classify(RestoreFixtureFactory.snapshot(sessionCandidates = listOf("s1", "s2"))))
    }

    @Test
    fun `chooser outcomes capture selected and canceled states`() {
        val chooser = object : StartupSessionChooser() {
            override fun choose(
                project: com.intellij.openapi.project.Project,
                requests: List<StartupSessionChooserRequest>,
            ): Map<String, StartupSessionChooserResult> {
                return mapOf(
                    requests[0].terminalTabId to StartupSessionChooserResult("s2", false),
                    requests[1].terminalTabId to StartupSessionChooserResult(null, true),
                )
            }
        }
        val requests = listOf(
            StartupSessionChooserRequest("t1", "Tab 1", listOf("s1", "s2")),
            StartupSessionChooserRequest("t2", "Tab 2", listOf("x1", "x2")),
        )
        val project = Proxy.newProxyInstance(
            com.intellij.openapi.project.Project::class.java.classLoader,
            arrayOf(com.intellij.openapi.project.Project::class.java),
        ) { _, _, _ -> null } as com.intellij.openapi.project.Project
        val results = chooser.choose(project = project, requests = requests)

        assertEquals("s2", results["t1"]?.selectedSessionReference)
        assertEquals(false, results["t1"]?.canceled)
        assertEquals(null, results["t2"]?.selectedSessionReference)
        assertEquals(true, results["t2"]?.canceled)
    }

    @Test
    fun `partition prefers explicit session reference over candidate ambiguity`() {
        val classifier = StartupAmbiguityClassifier()
        val explicitSnapshot = RestoreFixtureFactory.snapshot(
            sessionReference = "codex-1234",
            sessionCandidates = listOf("codex-1234", "codex-legacy-1", "codex-legacy-2"),
        )

        val buckets = classifier.partition(
            snapshots = listOf(explicitSnapshot),
            hasExplicitReference = { !it.sessionReference.isNullOrBlank() },
            useStartupChooser = true,
        )

        assertEquals(1, buckets.exact.size)
        assertEquals(0, buckets.ambiguous.size)
        assertEquals(0, buckets.fallback.size)
    }
}
