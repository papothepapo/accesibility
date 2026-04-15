package com.exalt.accessibilityswitcher.settings

class AccessibilitySettingsWriter(
    private val backend: AccessibilitySettingsBackend
) {
    fun applySelectedService(serviceComponent: String): WriteResult {
        val selected = normalizeComponent(serviceComponent)
            ?: return WriteResult.InvalidComponent(serviceComponent)

        val currentList = backend.getEnabledAccessibilityServices()
        val currentEnabled = backend.getAccessibilityEnabled()
        val listAlreadyExact = parseServiceList(currentList) == listOf(selected)
        val accessibilityAlreadyEnabled = currentEnabled == ENABLED

        if (listAlreadyExact && accessibilityAlreadyEnabled) {
            return WriteResult.NoChange(selected)
        }

        val changedList = !listAlreadyExact
        val changedEnabled = !accessibilityAlreadyEnabled

        if (changedList && !backend.setEnabledAccessibilityServices(selected)) {
            return WriteResult.Failed(selected, "Could not update enabled_accessibility_services")
        }

        if (changedEnabled && !backend.setAccessibilityEnabled(ENABLED)) {
            return WriteResult.Failed(selected, "Could not enable accessibility")
        }

        return WriteResult.Changed(
            serviceComponent = selected,
            changedServiceList = changedList,
            changedAccessibilityEnabled = changedEnabled
        )
    }

    fun currentServiceList(): List<String> = parseServiceList(backend.getEnabledAccessibilityServices())

    private fun normalizeComponent(serviceComponent: String): String? {
        val trimmed = serviceComponent.trim()
        if (!trimmed.contains("/")) {
            return null
        }
        return trimmed
    }

    private fun parseServiceList(raw: String?): List<String> {
        return raw
            ?.split(':')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    sealed class WriteResult {
        data class NoChange(val serviceComponent: String) : WriteResult()
        data class Changed(
            val serviceComponent: String,
            val changedServiceList: Boolean,
            val changedAccessibilityEnabled: Boolean
        ) : WriteResult()

        data class InvalidComponent(val attemptedComponent: String) : WriteResult()
        data class Failed(val serviceComponent: String, val reason: String) : WriteResult()
    }

    private companion object {
        const val ENABLED = "1"
    }
}
