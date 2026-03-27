package plugin.capture

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import plugin.runtime.RestoreRuntimeFactory

class CaptureCurrentSessionsIdeAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val summary = TerminalSnapshotCapture.capture(project)
        val content = "Captured ${summary.capturedSnapshots} snapshot(s) from ${summary.totalTabs} tab(s), active=${summary.activeSessions}."
        RestoreRuntimeFactory.notify(project, "Agent Session Capture", content, NotificationType.INFORMATION)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
