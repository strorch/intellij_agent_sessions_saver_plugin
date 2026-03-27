package plugin.restore.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import plugin.runtime.RestoreRuntimeFactory

class ManualRestoreIdeAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val restoredSessions = RestoreRuntimeFactory.forProject(project).manualRestore()
        val title = "Agent Session Restore"
        val content = if (restoredSessions == 0) {
            "No restorable sessions were found for this project."
        } else {
            "Restored $restoredSessions session(s)."
        }
        RestoreRuntimeFactory.notify(project, title, content, NotificationType.INFORMATION)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
