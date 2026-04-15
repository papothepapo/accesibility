package com.exalt.accessibilityswitcher.settings

import com.exalt.accessibilityswitcher.settings.AccessibilitySettingsWriter.WriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsWriterTest {
    @Test
    fun writesOnlyChosenServiceAndEnablesAccessibility() {
        val backend = FakeBackend(
            serviceList = "old.pkg/.Old:other.pkg/.Other",
            accessibilityFlag = "0"
        )
        val writer = AccessibilitySettingsWriter(backend)

        val result = writer.applySelectedService("new.pkg/.New")

        assertEquals("new.pkg/.New", backend.serviceList)
        assertEquals("1", backend.accessibilityFlag)
        assertEquals(1, backend.serviceListWrites)
        assertEquals(1, backend.accessibilityEnabledWrites)
        assertTrue(result is WriteResult.Changed)
    }

    @Test
    fun repeatedForegroundEventsDoNotRewriteUnchangedSettings() {
        val backend = FakeBackend(
            serviceList = "new.pkg/.New",
            accessibilityFlag = "1"
        )
        val writer = AccessibilitySettingsWriter(backend)

        val result = writer.applySelectedService("new.pkg/.New")

        assertTrue(result is WriteResult.NoChange)
        assertEquals(0, backend.serviceListWrites)
        assertEquals(0, backend.accessibilityEnabledWrites)
    }

    @Test
    fun collapsesListWhenChosenServiceIsPresentWithOthers() {
        val backend = FakeBackend(
            serviceList = "new.pkg/.New:other.pkg/.Other",
            accessibilityFlag = "1"
        )
        val writer = AccessibilitySettingsWriter(backend)

        val result = writer.applySelectedService("new.pkg/.New")

        assertEquals("new.pkg/.New", backend.serviceList)
        assertEquals(1, backend.serviceListWrites)
        assertEquals(0, backend.accessibilityEnabledWrites)
        assertTrue(result is WriteResult.Changed)
    }

    @Test
    fun invalidComponentIsRejectedWithoutWrites() {
        val backend = FakeBackend(
            serviceList = "old.pkg/.Old",
            accessibilityFlag = "1"
        )
        val writer = AccessibilitySettingsWriter(backend)

        val result = writer.applySelectedService("not-a-component")

        assertTrue(result is WriteResult.InvalidComponent)
        assertEquals("old.pkg/.Old", backend.serviceList)
        assertEquals(0, backend.serviceListWrites)
        assertEquals(0, backend.accessibilityEnabledWrites)
    }

    private class FakeBackend(
        var serviceList: String?,
        var accessibilityFlag: String?
    ) : AccessibilitySettingsBackend {
        var serviceListWrites: Int = 0
        var accessibilityEnabledWrites: Int = 0

        override fun getEnabledAccessibilityServices(): String? = serviceList

        override fun setEnabledAccessibilityServices(value: String): Boolean {
            serviceListWrites += 1
            serviceList = value
            return true
        }

        override fun getAccessibilityEnabled(): String? = accessibilityFlag

        override fun setAccessibilityEnabled(value: String): Boolean {
            accessibilityEnabledWrites += 1
            accessibilityFlag = value
            return true
        }
    }
}
