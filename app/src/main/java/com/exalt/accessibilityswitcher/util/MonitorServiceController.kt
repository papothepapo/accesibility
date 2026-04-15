package com.exalt.accessibilityswitcher.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.exalt.accessibilityswitcher.monitor.MonitorService

object MonitorServiceController {
    fun start(context: Context) {
        val intent = Intent(context, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MonitorService::class.java))
    }
}
