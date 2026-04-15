package com.exalt.accessibilityswitcher.resolver

import com.exalt.accessibilityswitcher.model.ManagedRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleResolverTest {
    private val resolver = RuleResolver()

    @Test
    fun firstEnabledMatchingRuleWins() {
        val rules = listOf(
            rule(id = "first", packageName = "com.example.maps", service = "svc.one/.First"),
            rule(id = "second", packageName = "com.example.maps", service = "svc.two/.Second")
        )

        val result = resolver.resolve(rules, "com.example.maps")

        assertEquals(
            RuleResolver.Resolution.SwitchTo("svc.one/.First", "first"),
            result
        )
    }

    @Test
    fun disabledMatchingRuleIsSkipped() {
        val rules = listOf(
            rule(
                id = "disabled",
                packageName = "com.example.messages",
                service = "svc.one/.First",
                enabled = false
            ),
            rule(id = "enabled", packageName = "com.example.messages", service = "svc.two/.Second")
        )

        val result = resolver.resolve(rules, "com.example.messages")

        assertEquals(
            RuleResolver.Resolution.SwitchTo("svc.two/.Second", "enabled"),
            result
        )
    }

    @Test
    fun noMatchKeepsCurrentService() {
        val result = resolver.resolve(
            listOf(rule(packageName = "com.example.maps", service = "svc.one/.First")),
            "com.example.music"
        )

        assertTrue(result is RuleResolver.Resolution.KeepCurrent)
    }

    @Test
    fun blankForegroundPackageKeepsCurrentService() {
        val result = resolver.resolve(
            listOf(rule(packageName = "com.example.maps", service = "svc.one/.First")),
            ""
        )

        assertTrue(result is RuleResolver.Resolution.KeepCurrent)
    }

    private fun rule(
        id: String = "rule",
        packageName: String,
        service: String,
        enabled: Boolean = true
    ): ManagedRule {
        return ManagedRule(
            id = id,
            packageName = packageName,
            serviceComponent = service,
            enabled = enabled
        )
    }
}
