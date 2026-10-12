package io.github.AlexIllinois2.snub.services

import android.app.Notification
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.receiver.ScreenOffReceiver
import io.github.AlexIllinois2.snub.utils.HShell
import io.github.AlexIllinois2.snub.utils.HSystem
import io.github.AlexIllinois2.snub.utils.HUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoFreezeService : NotificationListenerService() {
    private val channelID = javaClass.simpleName
    private val lockReceiver by lazy { ScreenOffReceiver() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastForegroundTime = mutableMapOf<String, Long>()

    // Package to (last event timestamp, whether its activity is currently resumed),
    // maintained incrementally from UsageEvents, as aggregated UsageStats may lag
    // and keep a backgrounded app as the most recently used one forever.
    private val resumedStates = mutableMapOf<String, Pair<Long, Boolean>>()
    private var eventQueryStart = 0L
    private var eventsSeen = false
    private var usageAccessHintShown = false
    private var usageAccessWarned = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        usageAccessWarned = !HSystem.checkOpUsageStats(this)
        startForeground(100, buildNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.auto_freeze)
        val importance = NotificationManagerCompat.IMPORTANCE_LOW
        val channel = NotificationChannelCompat.Builder(channelID, importance).setName(name).build()
        // Register the channel with the system
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerScreenReceiver()
        scope.launch { backgroundMonitorLoop() }
    }

    /**
     * Monitors the foreground app and freezes apps in tags
     * with the background policy enabled after their delay.
     */
    private suspend fun backgroundMonitorLoop() {
        while (scope.isActive) {
            delay(10_000)
            val bgTagIds = HailData.tags.filter { it.autoFreezeBackground }.map { it.id }.toSet()
            if (bgTagIds.isEmpty()) continue
            val hasUsageAccess = HSystem.checkOpUsageStats(this@AutoFreezeService)
            updateUsageAccessWarning(hasUsageAccess)
            if (!hasUsageAccess) {
                if (!usageAccessHintShown) {
                    usageAccessHintShown = true
                    HUI.showToast(R.string.auto_freeze_usage_access, isLengthLong = true)
                }
                continue
            }
            val now = System.currentTimeMillis()
            val foregroundPkg = trackForegroundPackage(now)
            val usage: List<UsageStats>? by lazy { HSystem.queryUsageStats(this) }  // queried once if needed
            val charging = HailData.skipWhileCharging && HSystem.isCharging(this)
            val notifying = if (HailData.skipNotifyingApp) activeNotifications.map {
                it.packageName
            }.toSet() else emptySet()
            HailData.checkedList.forEach { appInfo ->
                if (appInfo.tagId !in bgTagIds) return@forEach
                val pkg = appInfo.packageName
                if (pkg == packageName || appInfo.whitelisted || appInfo.applicationInfo == null
                    || AppManager.isAppFrozen(pkg) || charging || pkg in notifying
                ) return@forEach
                if (pkg == foregroundPkg) {
                    lastForegroundTime[pkg] = now
                } else {
                    val tag = HailData.tags.find { it.id == appInfo.tagId } ?: return@forEach
                    val since = lastForegroundTime[pkg]
                        ?: usage?.filter { it.packageName == pkg }?.maxOfOrNull { it.lastTimeUsed }
                        ?: 0L
                    if (now - since >= tag.autoFreezeBackgroundDelay * 1000) {
                        if (!AppManager.setAppFrozen(pkg, true)) {
                            HUI.showToast(R.string.permission_denied)
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the package whose activity is currently resumed, or null when none is (e.g. on
     * the home screen or lock screen), which makes every background policy app eligible.
     *
     * Queries the live resumed activity first: UsageEvents may be delivered with long delays
     * (or not at all) on some ROMs, leaving the tracked state stale and blocking the freeze.
     * Falls back to incremental UsageEvents replay when dumpsys output is unavailable.
     */
    private fun trackForegroundPackage(now: Long): String? {
        val resumed = HShell.execute(RESUMED_ACTIVITY_COMMAND, true).second.orEmpty()
        if (resumed.isNotBlank()) return RESUMED_ACTIVITY_REGEX.find(resumed)?.groupValues?.getOrNull(1)
        return trackForegroundViaEvents(now)
    }

    private fun trackForegroundViaEvents(now: Long): String? {
        val start = if (eventQueryStart == 0L) now - EVENT_PRIME_WINDOW else eventQueryStart - EVENT_QUERY_OVERLAP
        eventQueryStart = now
        val batch = mutableListOf<Triple<Long, String, Int>>()
        val event = UsageEvents.Event()
        HSystem.queryUsageEvents(this, start, now)?.run {
            while (hasNextEvent()) {
                getNextEvent(event)
                // ACTIVITY_RESUMED/ACTIVITY_PAUSED equal MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND on older versions
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    || event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
                ) batch.add(Triple(event.timeStamp, event.packageName, event.eventType))
            }
        }
        if (batch.isEmpty() && !eventsSeen) return HSystem.queryUsageStats(this)
            ?.maxByOrNull { it.lastTimeUsed }?.packageName  // ROM delivers no events
        eventsSeen = true
        batch.sortBy { it.first }
        batch.forEach { (timeStamp, pkg, type) ->
            val state = resumedStates[pkg]
            if (state == null || timeStamp >= state.first) {
                resumedStates[pkg] = timeStamp to (type == UsageEvents.Event.ACTIVITY_RESUMED)
            }
        }
        return resumedStates.filter { it.value.second }.maxByOrNull { it.value.first }?.key
    }

    /** Reflects missing usage access in the persistent notification, as a one-shot toast is easy to miss. */
    private fun updateUsageAccessWarning(hasUsageAccess: Boolean) {
        if (hasUsageAccess == usageAccessWarned) return
        usageAccessWarned = !hasUsageAccess
        NotificationManagerCompat.from(this).notify(100, buildNotification())
    }

    private fun buildNotification(): Notification {
        val freezeAuto = PendingIntent.getActivity(
            applicationContext, 0, Intent(HailApi.ACTION_FREEZE_AUTO), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(getString(R.string.auto_freeze_notification_title))
            .setSmallIcon(R.drawable.ic_round_frozen)
            .addAction(R.drawable.ic_round_frozen, getString(R.string.auto_freeze), freezeAuto)
        if (HailData.checkedList.any { it.whitelisted }) {
            val freezeNonWhitelisted = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(HailApi.ACTION_FREEZE_NON_WHITELISTED),
                PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(
                R.drawable.ic_round_frozen,
                getString(R.string.action_freeze_non_whitelisted),
                freezeNonWhitelisted
            )
        }
        if (usageAccessWarned) notification.setContentText(getString(R.string.auto_freeze_usage_access))
        return notification.build()
    }

    private fun registerScreenReceiver() {
        registerReceiver(lockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unregisterReceiver(lockReceiver)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        lateinit var instance: AutoFreezeService private set

        /** Null when the service has never been created in this process. */
        val instanceOrNull: AutoFreezeService?
            get() = if (::instance.isInitialized) instance else null

        private const val EVENT_PRIME_WINDOW = 24 * 60 * 60 * 1000L
        private const val EVENT_QUERY_OVERLAP = 60 * 1000L

        private const val RESUMED_ACTIVITY_COMMAND =
            "dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity=|mResumedActivity=|ResumedActivity:'"
        private val RESUMED_ACTIVITY_REGEX = Regex("u\\d+ ([a-zA-Z0-9._]+)")
    }
}
