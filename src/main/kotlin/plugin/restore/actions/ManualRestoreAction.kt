package plugin.restore.actions

import plugin.persistence.TerminalSessionSnapshotStore
import plugin.restore.ProjectScopeGuard
import plugin.restore.RestoreCoordinator

class ManualRestoreAction(
    private val snapshotStore: TerminalSessionSnapshotStore,
    private val projectScopeGuard: ProjectScopeGuard,
    private val restoreCoordinator: RestoreCoordinator,
) {
    fun execute(projectScopeId: String): Int {
        val snapshots = snapshotStore.load(projectScopeId, currentSchemaVersion = 3)
        val scoped = projectScopeGuard.filterByProject(snapshots, projectScopeId).filter { it.hadActiveAgentSession }
        return restoreCoordinator.restore(projectScopeId, scoped, timeoutSeconds = 30).size
    }
}
