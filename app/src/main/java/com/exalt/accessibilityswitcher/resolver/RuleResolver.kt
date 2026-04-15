package com.exalt.accessibilityswitcher.resolver

import com.exalt.accessibilityswitcher.model.ManagedRule

class RuleResolver {
    fun resolve(rules: List<ManagedRule>, foregroundPackage: String?): Resolution {
        val packageName = foregroundPackage?.trim()
        if (packageName.isNullOrEmpty()) {
            return Resolution.KeepCurrent
        }

        val match = rules.firstOrNull { rule ->
            rule.enabled && rule.isComplete && rule.packageName == packageName
        }

        return if (match == null) {
            Resolution.KeepCurrent
        } else {
            Resolution.SwitchTo(match.serviceComponent, match.id)
        }
    }

    sealed class Resolution {
        object KeepCurrent : Resolution()
        data class SwitchTo(val serviceComponent: String, val ruleId: String) : Resolution()
    }
}
