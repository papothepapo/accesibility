package com.exalt.accessibilityswitcher.model

data class ManagedRule(
    val id: String,
    val packageName: String,
    val serviceComponent: String,
    val enabled: Boolean = true
) {
    val isComplete: Boolean
        get() = packageName.isNotBlank() && serviceComponent.contains("/")
}
