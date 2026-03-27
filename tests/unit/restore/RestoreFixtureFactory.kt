package tests.unit.restore

import plugin.persistence.model.TerminalSessionSnapshot

object RestoreFixtureFactory {
    fun snapshot(
        terminalTabId: String = "tab-1",
        agentType: String = "codex",
        sessionReference: String? = "session-1",
        sessionCandidates: List<String> = listOfNotNull(sessionReference),
        projectScopeId: String = "project-a",
        active: Boolean = true,
    ): TerminalSessionSnapshot {
        return TerminalSessionSnapshot(
            snapshotId = "snap-$terminalTabId",
            schemaVersion = 3,
            projectScopeId = projectScopeId,
            terminalTabId = terminalTabId,
            terminalDisplayName = terminalTabId,
            agentType = agentType,
            sessionReference = sessionReference,
            sessionReferenceSource = if (sessionReference == null) null else "COMMAND_LINE",
            sessionReferenceConfidence = if (sessionReference == null) null else "HIGH",
            sessionCandidates = sessionCandidates,
            processId = null,
            processCommand = null,
            hadActiveAgentSession = active,
            capturedAt = 1L,
            lastKnownStatus = "ready",
        )
    }
}
