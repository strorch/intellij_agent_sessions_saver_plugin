package plugin.persistence

import plugin.adapters.TerminalTabMapper
import plugin.adapters.TerminalTabState
import plugin.persistence.model.TerminalSessionSnapshot

class SessionSnapshotCaptureService(
    private val tabMapper: TerminalTabMapper,
) {
    fun capture(projectScopeId: String, tabs: List<TerminalTabState>): List<TerminalSessionSnapshot> {
        return tabMapper.toSessionState(tabs).map { tab ->
            TerminalSessionSnapshot(
                snapshotId = "snap-${tab.terminalTabId}",
                schemaVersion = 3,
                projectScopeId = projectScopeId,
                terminalTabId = tab.terminalTabId,
                terminalDisplayName = tab.terminalDisplayName,
                agentType = tab.agentType,
                sessionReference = tab.sessionReference,
                sessionReferenceSource = tab.sessionReferenceSource,
                sessionReferenceConfidence = tab.sessionReferenceConfidence,
                sessionCandidates = tab.sessionCandidates,
                processId = tab.processId,
                processCommand = tab.processCommand,
                hadActiveAgentSession = tab.hasActiveAgentSession,
                capturedAt = System.currentTimeMillis(),
                lastKnownStatus = "ready",
            )
        }
    }
}
