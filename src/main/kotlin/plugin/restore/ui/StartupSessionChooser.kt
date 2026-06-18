package plugin.restore.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

data class StartupSessionChooserRequest(
    val terminalTabId: String,
    val terminalDisplayName: String,
    val candidates: List<String>,
)

data class StartupSessionChooserResult(
    val selectedSessionReference: String?,
    val canceled: Boolean,
)

open class StartupSessionChooser {
    open fun choose(
        project: Project,
        requests: List<StartupSessionChooserRequest>,
    ): Map<String, StartupSessionChooserResult> {
        val app = ApplicationManager.getApplication()
        if (app.isHeadlessEnvironment || app.isUnitTestMode) {
            return requests.associate { it.terminalTabId to StartupSessionChooserResult(null, true) }
        }
        val selections = mutableMapOf<String, StartupSessionChooserResult>()
        requests.forEach { request ->
            if (request.candidates.size <= 1) {
                selections[request.terminalTabId] = StartupSessionChooserResult(request.candidates.firstOrNull(), false)
                return@forEach
            }
            val options = request.candidates + "Use Fallback"
            val selectedIndex = Messages.showDialog(
                project,
                "Multiple session IDs were detected for '${request.terminalDisplayName}'. Select the exact session to resume.",
                "Choose Session ID",
                options.toTypedArray(),
                options.lastIndex,
                Messages.getQuestionIcon(),
            )
            if (selectedIndex < 0 || selectedIndex == options.lastIndex) {
                selections[request.terminalTabId] = StartupSessionChooserResult(null, true)
            } else {
                selections[request.terminalTabId] = StartupSessionChooserResult(options[selectedIndex], false)
            }
        }
        return selections
    }
}
