package io.github.AlexIllinois2.snub.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.getSystemService

object HSystem {
    fun isInteractive(context: Context): Boolean {
        val powerManger = context.getSystemService<PowerManager>()!!
        return powerManger.isInteractive
    }

    fun isCharging(context: Context): Boolean {
        val batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Returns the package whose activity is currently resumed, or null when none is
     * (e.g. on the home screen or lock screen). Queried live as root via dumpsys:
     * aggregated UsageStats and UsageEvents may lag for a long time (or not be
     * delivered at all) on some ROMs, and usage access permission is not required.
     */
    fun getForegroundPackageName(): String? = HShell.execute(
        "dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity=|mResumedActivity=|ResumedActivity:'",
        true
    ).second.orEmpty().let { output ->
        RESUMED_ACTIVITY_REGEX.find(output)?.groupValues?.getOrNull(1)
    }

    fun isForegroundApp(packageName: String): Boolean = getForegroundPackageName() == packageName

    private val RESUMED_ACTIVITY_REGEX = Regex("u\\d+ ([a-zA-Z0-9._]+)")
}
