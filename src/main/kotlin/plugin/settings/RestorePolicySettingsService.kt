package plugin.settings

import plugin.persistence.model.RestorePolicy

class RestorePolicySettingsService {
    private var currentPolicy = RestorePolicy()

    fun readPolicy(): RestorePolicy {
        return currentPolicy
    }

    fun updatePolicy(policy: RestorePolicy) {
        currentPolicy = policy
    }

    fun isStartupRestoreEnabled(): Boolean {
        return currentPolicy.autoRestoreEnabled && !currentPolicy.manualRestoreMode
    }
}
