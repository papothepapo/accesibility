package com.exalt.accessibilityswitcher.settings

interface AccessibilitySettingsBackend {
    fun getEnabledAccessibilityServices(): String?
    fun setEnabledAccessibilityServices(value: String): Boolean
    fun getAccessibilityEnabled(): String?
    fun setAccessibilityEnabled(value: String): Boolean
}
