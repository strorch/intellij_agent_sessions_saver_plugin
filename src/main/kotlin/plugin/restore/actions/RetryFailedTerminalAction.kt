package plugin.restore.actions

import plugin.persistence.model.TerminalSessionSnapshot
import plugin.restore.RestoreCoordinator

class RetryFailedTerminalAction(
    private val restoreCoordinator: RestoreCoordinator,
) {
    fun execute(projectScopeId: String, snapshot: TerminalSessionSnapshot): Int {
        val results = restoreCoordinator.restore(projectScopeId, listOf(snapshot), timeoutSeconds = 30)
        return results.size
    }
}
