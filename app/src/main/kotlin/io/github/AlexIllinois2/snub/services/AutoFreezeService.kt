package io.github.AlexIllinois2.snub.services

import android.app.Notification
import android.app.PendingIntent
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
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
            val now = System.currentTimeMillis()
            val foregroundPkg = HSystem.getForegroundPackageName()
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
                    // Unknown "last foreground" (e.g. backgrounded before this service started)
                    // is treated as just now, so the delay always applies before freezing.
                    val since = lastForegroundTime[pkg] ?: now
                    if (now - since >= tag.autoFreezeBackgroundDelay * 1000) {
                        if (!AppManager.setAppFrozen(pkg, true)) {
                            HUI.showToast(R.string.permission_denied)
                        }
                    }
                }
            }
        }
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
    }
}
