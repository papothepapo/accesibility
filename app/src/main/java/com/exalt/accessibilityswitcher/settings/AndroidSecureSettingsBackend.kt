package com.exalt.accessibilityswitcher.settings

import android.content.ContentResolver
import android.provider.Settings

class AndroidSecureSettingsBackend(
    private val contentResolver: ContentResolver
) : AccessibilitySettingsBackend {
    override fun getEnabledAccessibilityServices(): String? {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    }

    override fun setEnabledAccessibilityServices(value: String): Boolean {
        return Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            value
        )
    }

    override fun getAccessibilityEnabled(): String? {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    }

    override fun setAccessibilityEnabled(value: String): Boolean {
        return Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            value
        )
    }
}
