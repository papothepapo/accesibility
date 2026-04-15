package com.exalt.accessibilityswitcher.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlin.math.max

class ForegroundAppReader(context: Context) {
    private val usageStatsManager =
        context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun latestForegroundPackage(lookBackMillis: Long): String? {
        val end = System.currentTimeMillis()
        val start = max(0L, end - lookBackMillis)
        val events = usageStatsManager.queryEvents(start, end) ?: return null
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (isForegroundEvent(event) && event.packageName != null && event.timeStamp >= latestTime) {
                latestTime = event.timeStamp
                latestPackage = event.packageName
            }
        }

        return latestPackage
    }

    private fun isForegroundEvent(event: UsageEvents.Event): Boolean {
        return event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
    }
}
