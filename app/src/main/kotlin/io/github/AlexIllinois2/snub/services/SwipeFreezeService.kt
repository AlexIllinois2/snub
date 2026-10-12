package io.github.AlexIllinois2.snub.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.ui.main.MainActivity
import io.github.AlexIllinois2.snub.utils.HShell
import io.github.AlexIllinois2.snub.utils.HTarget
import io.github.AlexIllinois2.snub.utils.HUI
import io.github.AlexIllinois2.snub.utils.TempUnfrozenList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Auto freezes checked apps that are absent from recents, so that every managed app
 * absent from the recents screen ends up frozen, even if it was swiped away or its
 * task was never seen (e.g. before this service started).
 * Apps with a task in recents are never touched.
 *
 * Requires a root working mode: it polls [DUMP_RECENTS_COMMAND] via a root shell.
 */
class SwipeFreezeService : Service() {
    private val channelID = javaClass.simpleName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        startWatchdog()
        scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopWatchdog()
        scope.cancel()
        TempUnfrozenList.clear()
        super.onDestroy()
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(getString(R.string.swipe_freeze_notification_title))
            .setSmallIcon(R.drawable.ic_round_frozen)
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

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            channelID, NotificationManagerCompat.IMPORTANCE_LOW
        ).setName(getString(R.string.swipe_freeze)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private val pollIntervalMillis: Long
        get() {
            val seconds = HailData.swipeFreezeInterval
            val interactive = getSystemService<PowerManager>()?.isInteractive == true
            return ((if (interactive) seconds else seconds * IDLE_INTERVAL_FACTOR) * 1000).toLong()
        }

    private suspend fun CoroutineScope.pollLoop() {
        var lastSeedAt = 0L
        var abnormalPolls = 0
        while (isActive) {
            delay(pollIntervalMillis)
            // Re-seed periodically: apps launched directly from the launcher (clearing their
            // stopped state, e.g. with the "stop" working mode) bypass setAppFrozen and would
            // otherwise never be tracked for swipe-away freezing.
            val now = System.currentTimeMillis()
            if (now - lastSeedAt >= RESEED_INTERVAL_MILLIS) {
                seedCheckedApps()
                lastSeedAt = now
            }
            val snapshot = TempUnfrozenList.snapshot()
            if (snapshot.isEmpty()) continue

            val dump = HShell.execute(DUMP_RECENTS_COMMAND, true).second.orEmpty()
            if (dump.isBlank()) {
                // dumpsys keeps returning nothing: the ROM may be incompatible, stop before misfreezing.
                if (++abnormalPolls >= ABNORMAL_POLLS_LIMIT) {
                    HUI.showToast(R.string.swipe_freeze_abnormal, isLengthLong = true)
                    stopSelf()
                    break
                }
                continue
            }
            abnormalPolls = 0

            // Android 14+ keeps swiped-away tasks in `mHiddenTasks` (still printed by dumpsys),
            // so matching the whole dump would treat swiped-away apps as "still in recents" and
            // never freeze them. Match live task entries only.
            val recents = dump.toLiveTaskText()

            snapshot.forEach { (packageName, unfrozenAt) ->
                // Purge entries of apps that are no longer managed (unchecked or whitelisted
                // after being unfrozen), so unchecking an app always stops its auto freezing.
                if (!HailData.isChecked(packageName) || HailData.isWhitelisted(packageName)) {
                    TempUnfrozenList.remove(packageName)
                    return@forEach
                }
                if (now - unfrozenAt < (HailData.swipeFreezeDelay * 1000).toLong()) return@forEach
                if (!recents.containsPackage(packageName)) freezeApp(packageName)
            }
        }
    }

    private fun seedCheckedApps() {
        HailData.checkedList.filterNot {
            it.whitelisted || it.packageName == BuildConfig.APPLICATION_ID
        }.forEach {
            if (!AppManager.isAppFrozen(it.packageName)) TempUnfrozenList.add(it.packageName)
        }
    }

    private fun freezeApp(packageName: String) {
        TempUnfrozenList.remove(packageName)
        if (!AppManager.setAppFrozen(packageName, true)) {
            HUI.showToast(R.string.permission_denied)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 101
        const val ABNORMAL_POLLS_LIMIT = 30
        const val RESEED_INTERVAL_MILLIS = 30_000L
        const val IDLE_INTERVAL_FACTOR = 10
        const val DUMP_RECENTS_COMMAND = "dumpsys activity recents"
    }

    /**
     * Keeps this service alive when the ROM kills Hail on task swipe (cancelling START_STICKY
     * restarts): a root watchdog loop revives it within seconds. It is root-only, as is this service.
     */
    private fun startWatchdog() {
        HShell.execute("touch '$watchdogMarker'", true)
        // `hail_[w]d` never matches this check's own command line, unlike a plain `hail_wd`.
        if (HShell.execute("pgrep -f hail_[w]d", true).second.isNullOrBlank()) HShell.spawnRoot(
            "while true; do sleep 10; " +
                "[ -f '$watchdogMarker' ] || break; " + // Feature disabled
                "[ -n \"$(pm path $packageName 2>/dev/null)\" ] || break; " + // App uninstalled
                "pidof $packageName >/dev/null 2>&1 || " +
                "am start-foreground-service -n $packageName/.services.SwipeFreezeService; " +
                "done # hail_wd" // Tag for pgrep
        )
    }

    private fun stopWatchdog() {
        HShell.execute("rm -f '$watchdogMarker'", true) // The watchdog exits when it sees this
    }

    private val watchdogMarker: String
        get() = File(filesDir, ".swipe_freeze_wd").absolutePath
}

/** Matches [packageName] as a whole word, so that `com.foo` does not match `com.foobar`. */
private fun String.containsPackage(packageName: String): Boolean {
    var index = indexOf(packageName)
    while (index >= 0) {
        val before = getOrNull(index - 1)
        val after = getOrNull(index + packageName.length)
        if ((before == null || !before.isPackageChar()) && (after == null || !after.isPackageChar())) return true
        index = indexOf(packageName, index + 1)
    }
    return false
}

private fun Char.isPackageChar(): Boolean = isLetterOrDigit() || this == '.' || this == '_'

/**
 * Extracts the text of live task entries (`* Recent #N: Task{...}` blocks on newer Androids,
 * `*Task{...}` blocks on older ones) from a `dumpsys activity recents` dump.
 * Everything else (e.g. the Android 14+ `mHiddenTasks` list of swiped-away tasks) is dropped,
 * so packages mentioned there are no longer treated as present in recents.
 */
private fun String.toLiveTaskText(): String {
    val live = StringBuilder()
    var taskIndent = -1
    lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("*") -> { // Task entry header: `* Recent #N: Task{` or `*Task{`
                taskIndent = line.length - trimmed.length
                live.appendLine(line)
            }
            taskIndent >= 0 && line.length - trimmed.length > taskIndent -> live.appendLine(line)
            else -> taskIndent = -1 // Non-indented line: the task block has ended
        }
    }
    return live.toString()
}
