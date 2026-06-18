package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.capture.AgentSessionDetector
import plugin.capture.DetectionSource

class AgentSessionDetectorTest {
    @Test
    fun `detects opencode from process command line even with claude text noise`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "opencode --session oc-12345678",
                typedCommand = "",
                shellCommand = "",
                terminalText = "some old claude output",
            ),
        )

        assertEquals("opencode", detection.agentType)
        assertEquals("oc-12345678", detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `detects copilot from gh copilot command line`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "gh copilot --resume=cp-42000000",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("copilot", detection.agentType)
        assertEquals("cp-42000000", detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `keeps copilot classification when claude model name appears`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "copilot chat --model claude-sonnet",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("copilot", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `keeps opencode classification when claude model name appears`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "opencode --model claude-3.7",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("opencode", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `ignores version-like values as session id`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "opencode --session .3.2",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("opencode", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `ignores synthetic detected values as session id`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "codex resume detected-codex-legacy",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("codex", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `marks active process even when explicit session id is unavailable`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "/usr/bin/codex",
                typedCommand = "",
                shellCommand = "",
                terminalText = "",
            ),
        )

        assertEquals("codex", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }

    @Test
    fun `fallback text detection marks detected agent as active without id`() {
        val detection = AgentSessionDetector.detect(
            DetectionSource(
                processCommandLine = "",
                typedCommand = "",
                shellCommand = "",
                terminalText = "claude welcome screen",
            ),
        )

        assertEquals("claude", detection.agentType)
        assertEquals(null, detection.sessionReference)
        assertTrue(detection.hasActiveSession)
    }
}
