package plugin.restore

import plugin.persistence.TerminalSessionSnapshotStore
import plugin.persistence.model.RestorePolicy
import plugin.persistence.model.RestoreAttemptResult
import plugin.settings.RestorePolicySettingsService

class StartupRestoreActivity(
    private val snapshotStore: TerminalSessionSnapshotStore,
    private val settingsService: RestorePolicySettingsService,
    private val projectScopeGuard: ProjectScopeGuard,
    private val restoreCoordinator: RestoreCoordinator,
) {
    fun runStartupRestore(projectScopeId: String): List<RestoreAttemptResult> {
        val policy = settingsService.readPolicy()
        if (!isAutoRestoreEnabled(policy)) {
            return emptyList()
        }
        val snapshots = snapshotStore.load(projectScopeId, currentSchemaVersion = 3)
        val scoped = projectScopeGuard.filterByProject(snapshots, projectScopeId)
        val activeCandidates = scoped.filter { it.hadActiveAgentSession }
        return restoreCoordinator.restore(projectScopeId, activeCandidates, policy.attemptTimeoutSeconds)
    }

    private fun isAutoRestoreEnabled(policy: RestorePolicy): Boolean {
        return policy.autoRestoreEnabled && !policy.manualRestoreMode
    }
}
