package com.exalt.accessibilityswitcher.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.exalt.accessibilityswitcher.R
import com.exalt.accessibilityswitcher.data.RuleStore
import com.exalt.accessibilityswitcher.resolver.RuleResolver
import com.exalt.accessibilityswitcher.settings.AccessibilitySettingsWriter
import com.exalt.accessibilityswitcher.settings.AndroidSecureSettingsBackend
import com.exalt.accessibilityswitcher.ui.MainActivity
import com.exalt.accessibilityswitcher.util.PermissionStatus

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: RuleStore
    private lateinit var foregroundAppReader: ForegroundAppReader
    private lateinit var resolver: RuleResolver
    private lateinit var settingsWriter: AccessibilitySettingsWriter

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, POLL_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RuleStore(this)
        foregroundAppReader = ForegroundAppReader(this)
        resolver = RuleResolver()
        settingsWriter = AccessibilitySettingsWriter(AndroidSecureSettingsBackend(contentResolver))
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pollOnce() {
        if (!store.isAutomationEnabled()) {
            store.setLastError("Automation is disabled")
            stopSelf()
            return
        }

        if (!PermissionStatus.hasUsageAccess(this)) {
            store.setLastError("Usage Access is not granted")
            return
        }

        if (!PermissionStatus.hasWriteSecureSettings(this)) {
            store.setLastError("WRITE_SECURE_SETTINGS is not granted")
            return
        }

        val packageName = try {
            foregroundAppReader.latestForegroundPackage(FOREGROUND_LOOKBACK_MILLIS)
        } catch (error: SecurityException) {
            store.setLastError("Usage Access read failed: ${error.message.orEmpty()}")
            return
        } catch (error: RuntimeException) {
            store.setLastError("Foreground read failed: ${error.message.orEmpty()}")
            return
        }

        if (!packageName.isNullOrBlank()) {
            store.setLastActivePackage(packageName)
        }

        if (store.isHoldEnabled()) {
            store.setLastError("Hold is active; keeping current service")
            return
        }

        when (val resolution = resolver.resolve(store.getRules(), packageName)) {
            RuleResolver.Resolution.KeepCurrent -> {
                store.setLastError("No matching enabled rule; keeping current service")
            }
            is RuleResolver.Resolution.SwitchTo -> applyService(resolution.serviceComponent)
        }
    }

    private fun applyService(serviceComponent: String) {
        val result = try {
            settingsWriter.applySelectedService(serviceComponent)
        } catch (error: SecurityException) {
            store.setLastError("Secure settings write denied: ${error.message.orEmpty()}")
            return
        } catch (error: RuntimeException) {
            store.setLastError("Secure settings write failed: ${error.message.orEmpty()}")
            return
        }

        when (result) {
            is AccessibilitySettingsWriter.WriteResult.Changed -> {
                store.setLastAppliedService(result.serviceComponent)
                store.setLastError("Applied ${result.serviceComponent}")
            }
            is AccessibilitySettingsWriter.WriteResult.NoChange -> {
                store.setLastAppliedService(result.serviceComponent)
                store.setLastError("Already active: ${result.serviceComponent}")
            }
            is AccessibilitySettingsWriter.WriteResult.InvalidComponent -> {
                store.setLastError("Invalid service component: ${result.attemptedComponent}")
            }
            is AccessibilitySettingsWriter.WriteResult.Failed -> {
                store.setLastError(result.reason)
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Accessibility automation",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, flags)

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_switcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Watching foreground apps")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "switcher_monitor"
        const val NOTIFICATION_ID = 1001
        const val POLL_INTERVAL_MILLIS = 1500L
        const val FOREGROUND_LOOKBACK_MILLIS = 12000L
    }
}
