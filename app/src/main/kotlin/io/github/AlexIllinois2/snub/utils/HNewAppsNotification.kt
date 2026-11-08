package io.github.AlexIllinois2.snub.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.ui.main.MainActivity

/**
 * Reminder for newly installed apps waiting to be confirmed, used when the app is in the
 * background and therefore cannot show the confirmation dialog right away.
 *
 * Only the number of waiting apps is shown, never their names, so nothing sensitive is
 * exposed on the lock screen. All waiting apps share a single notification.
 */
object HNewAppsNotification {
    private const val CHANNEL_ID = "NewAppsNotification"
    private const val NOTIFICATION_ID = 104 // 100-103 are taken by the foreground/alert notifications

    /**
     * Shows or updates the reminder. Does nothing when notifications are disabled, e.g. the
     * POST_NOTIFICATIONS permission was denied on Android 13+: the queue is then simply
     * asked about the next time the app comes to the foreground.
     */
    fun show(context: Context) {
        val count = HailData.pendingNewApps.size
        if (count == 0) {
            cancel(context)
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.new_apps_channel)).build()
        )
        if (!manager.areNotificationsEnabled()) return
        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_round_unfrozen)
            .setContentTitle(context.getString(R.string.new_app_installed))
            .setContentText(
                context.resources.getQuantityString(R.plurals.pending_new_apps, count, count)
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // Updates for further installs must not alert again
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
