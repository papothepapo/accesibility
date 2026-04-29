package com.exalt.accessibilityswitcher.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.exalt.accessibilityswitcher.R
import com.exalt.accessibilityswitcher.data.RuleStore
import com.exalt.accessibilityswitcher.model.ManagedRule
import com.exalt.accessibilityswitcher.resolver.RuleResolver
import com.exalt.accessibilityswitcher.settings.AccessibilitySettingsWriter
import com.exalt.accessibilityswitcher.settings.AndroidSecureSettingsBackend
import com.exalt.accessibilityswitcher.ui.MainActivity
import com.exalt.accessibilityswitcher.util.PermissionStatus

class MonitorService : Service() {
    private lateinit var monitorThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var powerManager: PowerManager
    private lateinit var store: RuleStore
    private lateinit var foregroundAppReader: ForegroundAppReader
    private lateinit var resolver: RuleResolver
    private lateinit var settingsWriter: AccessibilitySettingsWriter

    @Volatile
    private var screenInteractive = true
    private var lastObservedPackage: String? = null
    private var lastResolvedPackage: String? = null
    private var lastResolvedAtMillis: Long = 0L
    private var cachedRules: List<ManagedRule> = emptyList()
    private var cachedRulesVersion: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(pollRunnable)
                    handler.post {
                        screenInteractive = false
                        handler.removeCallbacks(pollRunnable)
                        store.setLastError("Screen off; monitor idle")
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    handler.post {
                        screenInteractive = true
                        handler.removeCallbacks(pollRunnable)
                        clearResolvedPackage()
                        scheduleNextPoll(0L)
                    }
                }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            scheduleNextPoll()
        }
    }

    override fun onCreate() {
        super.onCreate()
        monitorThread = HandlerThread("SwitcherMonitor")
        monitorThread.start()
        handler = Handler(monitorThread.looper)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenInteractive = isScreenInteractive()
        store = RuleStore(this)
        foregroundAppReader = ForegroundAppReader(this)
        resolver = RuleResolver()
        settingsWriter = AccessibilitySettingsWriter(AndroidSecureSettingsBackend(contentResolver))
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        lastObservedPackage = null
        clearResolvedPackage()
        scheduleNextPoll(0L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        unregisterReceiver(screenReceiver)
        monitorThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pollOnce() {
        if (!store.isAutomationEnabled()) {
            store.setLastError("Automation is disabled")
            stopSelf()
            return
        }

        if (!isScreenInteractive()) {
            screenInteractive = false
            store.setLastError("Screen off; monitor idle")
            return
        }
        screenInteractive = true

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

        val rules = getCachedRules()
        if (
            packageName == lastObservedPackage &&
            packageName == lastResolvedPackage &&
            !shouldRecheckResolvedPackage()
        ) {
            return
        }
        lastObservedPackage = packageName

        if (store.isHoldEnabled()) {
            store.setLastError("Hold is active; keeping current service")
            return
        }

        if (packageName == lastResolvedPackage && !shouldRecheckResolvedPackage()) {
            return
        }

        when (val resolution = resolver.resolve(rules, packageName)) {
            RuleResolver.Resolution.KeepCurrent -> {
                markResolvedPackage(packageName)
                store.setLastError("No matching enabled rule; keeping current service")
            }
            is RuleResolver.Resolution.SwitchTo -> {
                if (applyService(resolution.serviceComponent)) {
                    markResolvedPackage(packageName)
                } else {
                    clearResolvedPackage()
                }
            }
        }
    }

    private fun applyService(serviceComponent: String): Boolean {
        val result = try {
            settingsWriter.applySelectedService(serviceComponent)
        } catch (error: SecurityException) {
            store.setLastError("Secure settings write denied: ${error.message.orEmpty()}")
            return false
        } catch (error: RuntimeException) {
            store.setLastError("Secure settings write failed: ${error.message.orEmpty()}")
            return false
        }

        return when (result) {
            is AccessibilitySettingsWriter.WriteResult.Changed -> {
                store.setLastAppliedService(result.serviceComponent)
                store.setLastError("Applied ${result.serviceComponent}")
                true
            }
            is AccessibilitySettingsWriter.WriteResult.NoChange -> {
                store.setLastAppliedService(result.serviceComponent)
                store.setLastError("Already active: ${result.serviceComponent}")
                true
            }
            is AccessibilitySettingsWriter.WriteResult.InvalidComponent -> {
                store.setLastError("Invalid service component: ${result.attemptedComponent}")
                true
            }
            is AccessibilitySettingsWriter.WriteResult.Failed -> {
                store.setLastError(result.reason)
                false
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

    private fun scheduleNextPoll(delayMillis: Long = nextPollIntervalMillis()) {
        if (!store.isAutomationEnabled()) {
            stopSelf()
            return
        }
        if (!screenInteractive && delayMillis > 0L) {
            return
        }
        handler.postDelayed(pollRunnable, delayMillis)
    }

    private fun nextPollIntervalMillis(): Long {
        return if (screenInteractive) {
            SCREEN_ON_POLL_INTERVAL_MILLIS
        } else {
            SCREEN_OFF_POLL_INTERVAL_MILLIS
        }
    }

    private fun isScreenInteractive(): Boolean {
        return if (Build.VERSION.SDK_INT >= 20) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    private fun getCachedRules(): List<ManagedRule> {
        val version = store.getRulesVersion()
        if (version != cachedRulesVersion) {
            cachedRules = store.getRules()
            cachedRulesVersion = version
            clearResolvedPackage()
        }
        return cachedRules
    }

    private fun markResolvedPackage(packageName: String?) {
        lastResolvedPackage = packageName
        lastResolvedAtMillis = SystemClock.elapsedRealtime()
    }

    private fun clearResolvedPackage() {
        lastResolvedPackage = null
        lastResolvedAtMillis = 0L
    }

    private fun shouldRecheckResolvedPackage(): Boolean {
        return SystemClock.elapsedRealtime() - lastResolvedAtMillis >= SERVICE_RECHECK_INTERVAL_MILLIS
    }

    private companion object {
        const val CHANNEL_ID = "switcher_monitor"
        const val NOTIFICATION_ID = 1001
        const val SCREEN_ON_POLL_INTERVAL_MILLIS = 3500L
        const val SCREEN_OFF_POLL_INTERVAL_MILLIS = 60000L
        const val SERVICE_RECHECK_INTERVAL_MILLIS = 60000L
        const val FOREGROUND_LOOKBACK_MILLIS = 15000L
    }
}
