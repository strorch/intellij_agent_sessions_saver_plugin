package plugin.capture

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class ProjectCloseSnapshotListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        TerminalSnapshotCapture.capture(project)
    }
}
