package plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import plugin.persistence.model.RestorePolicy

/**
 * Application-level persistent settings backing the restore [RestorePolicy].
 *
 * The policy survives IDE restarts via [State] storage and can be edited through
 * the dedicated Settings configurable (auto vs. manual restore mode).
 */
@State(
    name = "AgentSessionsSaverRestorePolicy",
    storages = [Storage("agent-sessions-saver.xml")],
)
class RestorePolicySettingsService : PersistentStateComponent<RestorePolicySettingsService.State> {

    /**
     * Mutable, XML-serializable mirror of [RestorePolicy]. [RestorePolicy] itself stays an
     * immutable kotlinx-serialization model used by the persistence layer, so the state is kept
     * as a separate holder with `var` fields and a no-arg constructor.
     */
    class State {
        var autoRestoreEnabled: Boolean = RestorePolicy().autoRestoreEnabled
        var manualRestoreMode: Boolean = RestorePolicy().manualRestoreMode
        var autoRetryCount: Int = RestorePolicy().autoRetryCount
        var attemptTimeoutSeconds: Int = RestorePolicy().attemptTimeoutSeconds
        var scopeBoundary: String = RestorePolicy().scopeBoundary
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun readPolicy(): RestorePolicy {
        return RestorePolicy(
            autoRestoreEnabled = state.autoRestoreEnabled,
            manualRestoreMode = state.manualRestoreMode,
            autoRetryCount = state.autoRetryCount,
            attemptTimeoutSeconds = state.attemptTimeoutSeconds,
            scopeBoundary = state.scopeBoundary,
        )
    }

    fun updatePolicy(policy: RestorePolicy) {
        state.autoRestoreEnabled = policy.autoRestoreEnabled
        state.manualRestoreMode = policy.manualRestoreMode
        state.autoRetryCount = policy.autoRetryCount
        state.attemptTimeoutSeconds = policy.attemptTimeoutSeconds
        state.scopeBoundary = policy.scopeBoundary
    }

    fun isStartupRestoreEnabled(): Boolean {
        return state.autoRestoreEnabled && !state.manualRestoreMode
    }

    companion object {
        fun getInstance(): RestorePolicySettingsService =
            ApplicationManager.getApplication().getService(RestorePolicySettingsService::class.java)
    }
}
