package io.github.AlexIllinois2.snub.services

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.ui.main.MainActivity
import io.github.AlexIllinois2.snub.utils.HShell
import io.github.AlexIllinois2.snub.utils.HUI
import io.github.AlexIllinois2.snub.utils.HTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors the battery level and shuts the device down (root only) once it drops to
 * the configured threshold. A countdown notification with a cancel button is shown
 * before the shutdown; plugging in the charger also cancels the pending shutdown.
 * After a cancellation the service stays idle until charging or the level recovers.
 */
class LowBatteryShutdownService : Service() {
    private val channelID = javaClass.simpleName
    private val alertChannelID = "${channelID}_alert"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null
    private val armed = AtomicBoolean(true)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) onBatteryChanged(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startAsForeground()
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelCountdown()
            HUI.showToast(R.string.low_battery_cancelled)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        scope.cancel()
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onDestroy()
    }

    private fun onBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level < 0 || scale <= 0) 100 else level * 100 / scale
        if (isCharging(intent) || percent > HailData.lowBatteryLevel) {
            // Charging or recovered: cancel any pending shutdown and allow the next trigger.
            armed.set(true)
            cancelCountdown()
        } else if (armed.compareAndSet(true, false)) {
            startCountdown()
        }
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun startCountdown() {
        countdownJob = scope.launch {
            for (remaining in HailData.lowBatteryNotifySeconds downTo 1) {
                postCountdownNotification(remaining)
                delay(1000)
            }
            if (isActive) HShell.powerOff()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
    }

    private fun postCountdownNotification(remainingSeconds: Long) {
        NotificationManagerCompat.from(this).notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, alertChannelID)
                .setContentTitle(getString(R.string.low_battery_countdown_title, remainingSeconds))
                .setSmallIcon(R.drawable.ic_round_power)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .addAction(
                    0, getString(android.R.string.cancel),
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, LowBatteryShutdownService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        )
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(getString(R.string.low_battery_notification_title))
            .setSmallIcon(R.drawable.ic_round_power)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        when {
            HTarget.U -> startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannels() {
        NotificationManagerCompat.from(this).run {
            createNotificationChannel(
                NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(getString(R.string.low_battery_shutdown)).build()
            )
            createNotificationChannel(
                NotificationChannelCompat.Builder(alertChannelID, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(getString(R.string.low_battery_channel_alert)).build()
            )
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 102
        const val ALERT_NOTIFICATION_ID = 103
        val ACTION_CANCEL = "${BuildConfig.APPLICATION_ID}.action.CANCEL_LOW_BATTERY_SHUTDOWN"
    }
}
