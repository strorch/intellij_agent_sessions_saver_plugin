package plugin.restore.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import plugin.runtime.RestoreRuntimeFactory

class RetryFailedTerminalIdeAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val restoredSessions = RestoreRuntimeFactory.forProject(project).retryFailed()
        val title = "Retry Failed Agent Sessions"
        val content = if (restoredSessions == 0) {
            "No failed sessions found in the latest restore run."
        } else {
            "Retried and restored $restoredSessions session(s)."
        }
        RestoreRuntimeFactory.notify(project, title, content, NotificationType.INFORMATION)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
