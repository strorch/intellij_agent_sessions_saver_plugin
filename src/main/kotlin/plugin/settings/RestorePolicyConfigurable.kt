package plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Application-level Settings page that lets the user choose between automatic startup restore
 * and manual restore mode, persisting the choice via [RestorePolicySettingsService] (FR-011).
 */
class RestorePolicyConfigurable : Configurable {

    private enum class RestoreModeOption(val displayName: String) {
        AUTOMATIC("Automatic (restore on startup)"),
        MANUAL("Manual (restore only via Tools menu)"),
        ;

        override fun toString(): String = displayName
    }

    private val settings: RestorePolicySettingsService
        get() = RestorePolicySettingsService.getInstance()

    private var modeComboBox: ComboBox<RestoreModeOption>? = null

    override fun getDisplayName(): String = "Agent Sessions Saver"

    override fun createComponent(): JComponent {
        val comboBox = ComboBox(RestoreModeOption.entries.toTypedArray())
        modeComboBox = comboBox
        val panel: JPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Restore mode:"), comboBox, true)
            .addComponentToRightColumn(
                JBLabel("Manual mode disables automatic restore when a project is opened."),
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        return selectedMode() != currentMode()
    }

    override fun apply() {
        val selected = selectedMode()
        val policy = settings.readPolicy()
        settings.updatePolicy(
            policy.copy(
                autoRestoreEnabled = selected == RestoreModeOption.AUTOMATIC,
                manualRestoreMode = selected == RestoreModeOption.MANUAL,
            ),
        )
    }

    override fun reset() {
        modeComboBox?.selectedItem = currentMode()
    }

    override fun disposeUIResources() {
        modeComboBox = null
    }

    private fun selectedMode(): RestoreModeOption =
        modeComboBox?.selectedItem as? RestoreModeOption ?: RestoreModeOption.AUTOMATIC

    private fun currentMode(): RestoreModeOption =
        if (settings.isStartupRestoreEnabled()) RestoreModeOption.AUTOMATIC else RestoreModeOption.MANUAL
}
