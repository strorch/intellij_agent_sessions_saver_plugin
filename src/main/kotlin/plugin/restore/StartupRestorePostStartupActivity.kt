package plugin.restore

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import plugin.runtime.RestoreRuntimeFactory

class StartupRestorePostStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val app = ApplicationManager.getApplication()
        if (app.isHeadlessEnvironment || app.isUnitTestMode) {
            return
        }
        // Restore runs on a background/pooled thread so the per-attempt timeout (enforced by waiting
        // on a Future in RestoreAttemptRunner) never blocks the EDT. The restore flow marshals its
        // EDT-only work (terminal widget enumeration/dispatch, session chooser) back to the EDT
        // itself. Only the resulting notification is posted on the EDT (RestoreRuntimeFactory.notify).
        app.executeOnPooledThread {
            if (project.isDisposed) {
                return@executeOnPooledThread
            }
            val summary = RestoreRuntimeFactory.forProject(project).startupRestore()
            // Report skipped restores explicitly so a skip-only run is never disguised as a generic
            // "Plugin loaded" message, and raise a WARNING when restores were skipped without recovery (#4).
            val content = when {
                summary.attempted > 0 -> "Startup restore attempted ${summary.attempted} tab(s): restored ${summary.restored} " +
                    "(exact=${summary.exactRestored}, chooser=${summary.chooserResolved}, fallback=${summary.fallbackRestored}, skipped=${summary.skipped})."
                summary.skipped > 0 -> "Startup restore skipped ${summary.skipped} saved tab(s); none could be restored. " +
                    "Use 'Capture Agent Sessions Now' from Tools or Find Action."
                else -> "Plugin loaded. Use 'Capture Agent Sessions Now' from Tools or Find Action."
            }
            val notificationType = if (summary.skipped > 0 && summary.restored < summary.skipped) {
                NotificationType.WARNING
            } else {
                NotificationType.INFORMATION
            }
            RestoreRuntimeFactory.notify(project, "Agent Session Restore", content, notificationType)
        }
    }
}
