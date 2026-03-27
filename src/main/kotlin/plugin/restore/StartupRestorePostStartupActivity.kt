package plugin.restore

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import plugin.runtime.RestoreRuntimeFactory

class StartupRestorePostStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val app = ApplicationManager.getApplication()
        if (app.isHeadlessEnvironment || app.isUnitTestMode) {
            return
        }
        app.invokeLater(
            {
                if (project.isDisposed) {
                    return@invokeLater
                }
                val summary = RestoreRuntimeFactory.forProject(project).startupRestore()
                val content = if (summary.attempted > 0) {
                    "Startup restore attempted ${summary.attempted} tab(s): restored ${summary.restored} " +
                        "(exact=${summary.exactRestored}, chooser=${summary.chooserResolved}, fallback=${summary.fallbackRestored}, skipped=${summary.skipped})."
                } else {
                    "Plugin loaded. Use 'Capture Agent Sessions Now' from Tools or Find Action."
                }
                RestoreRuntimeFactory.notify(project, "Agent Session Restore", content, NotificationType.INFORMATION)
            },
            ModalityState.NON_MODAL,
        )
    }
}
