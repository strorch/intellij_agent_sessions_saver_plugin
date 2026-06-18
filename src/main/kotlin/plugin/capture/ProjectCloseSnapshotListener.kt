package plugin.capture

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import plugin.runtime.RestoreRuntimeFactory

class ProjectCloseSnapshotListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        try {
            // captureOnClose performs the off-EDT resolve + bounded wait + synchronous
            // persist-before-return flow; it is the last consumer of the per-project
            // RestoreRuntime and must complete before the runtime is evicted.
            TerminalSnapshotCapture.captureOnClose(project)
        } finally {
            // Capture above is the last consumer of the per-project RestoreRuntime;
            // evict it afterwards so the cached entry (and its Project reference) is released.
            RestoreRuntimeFactory.evict(project)
        }
    }
}
