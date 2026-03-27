package plugin.restore

import plugin.persistence.model.TerminalSessionSnapshot

class ProjectScopeGuard {
    fun filterByProject(
        snapshots: List<TerminalSessionSnapshot>,
        projectScopeId: String,
    ): List<TerminalSessionSnapshot> {
        return snapshots.filter { it.projectScopeId == projectScopeId }
    }
}
