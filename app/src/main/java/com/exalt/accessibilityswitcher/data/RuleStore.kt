package com.exalt.accessibilityswitcher.data

import android.content.Context
import com.exalt.accessibilityswitcher.model.ManagedRule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RuleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRules(): List<ManagedRule> {
        val raw = prefs.getString(KEY_RULES, "[]") ?: "[]"
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val rules = mutableListOf<ManagedRule>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rule = ManagedRule(
                id = item.optString(FIELD_ID, UUID.randomUUID().toString()),
                packageName = item.optString(FIELD_PACKAGE),
                serviceComponent = item.optString(FIELD_SERVICE),
                enabled = item.optBoolean(FIELD_ENABLED, true)
            )
            rules += rule
        }
        return rules
    }

    fun getRulesVersion(): String = prefs.getString(KEY_RULES, "[]") ?: "[]"

    fun saveRules(rules: List<ManagedRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put(FIELD_ID, rule.id)
                    .put(FIELD_PACKAGE, rule.packageName)
                    .put(FIELD_SERVICE, rule.serviceComponent)
                    .put(FIELD_ENABLED, rule.enabled)
            )
        }
        putStringIfChanged(KEY_RULES, array.toString())
    }

    fun newRule(packageName: String, serviceComponent: String, enabled: Boolean = true): ManagedRule {
        return ManagedRule(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            serviceComponent = serviceComponent,
            enabled = enabled
        )
    }

    fun isAutomationEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMATION_ENABLED, false)

    fun setAutomationEnabled(enabled: Boolean) {
        putBooleanIfChanged(KEY_AUTOMATION_ENABLED, enabled)
    }

    fun isHoldEnabled(): Boolean = prefs.getBoolean(KEY_HOLD_ENABLED, false)

    fun setHoldEnabled(enabled: Boolean) {
        putBooleanIfChanged(KEY_HOLD_ENABLED, enabled)
    }

    fun getLastActivePackage(): String = prefs.getString(KEY_LAST_ACTIVE_PACKAGE, "") ?: ""

    fun setLastActivePackage(packageName: String) {
        putStringIfChanged(KEY_LAST_ACTIVE_PACKAGE, packageName)
    }

    fun getLastAppliedService(): String = prefs.getString(KEY_LAST_APPLIED_SERVICE, "") ?: ""

    fun setLastAppliedService(serviceComponent: String) {
        putStringIfChanged(KEY_LAST_APPLIED_SERVICE, serviceComponent)
    }

    fun getLastError(): String = prefs.getString(KEY_LAST_ERROR, "") ?: ""

    fun setLastError(message: String) {
        putStringIfChanged(KEY_LAST_ERROR, message)
    }

    private fun putStringIfChanged(key: String, value: String) {
        if (prefs.getString(key, null) != value) {
            prefs.edit().putString(key, value).apply()
        }
    }

    private fun putBooleanIfChanged(key: String, value: Boolean) {
        if (prefs.getBoolean(key, !value) != value) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        private const val PREFS = "switcher_state"
        private const val KEY_RULES = "rules"
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
        private const val KEY_HOLD_ENABLED = "hold_enabled"
        private const val KEY_LAST_ACTIVE_PACKAGE = "last_active_package"
        private const val KEY_LAST_APPLIED_SERVICE = "last_applied_service"
        private const val KEY_LAST_ERROR = "last_error"

        private const val FIELD_ID = "id"
        private const val FIELD_PACKAGE = "package"
        private const val FIELD_SERVICE = "service"
        private const val FIELD_ENABLED = "enabled"
    }
}
