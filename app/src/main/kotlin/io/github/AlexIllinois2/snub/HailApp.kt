package io.github.AlexIllinois2.snub

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.services.AutoFreezeService
import io.github.AlexIllinois2.snub.services.LowBatteryShutdownService
import io.github.AlexIllinois2.snub.services.SwipeFreezeService
import io.github.AlexIllinois2.snub.utils.HCrash
import io.github.AlexIllinois2.snub.utils.HLog
import io.github.AlexIllinois2.snub.utils.HTarget

class HailApp : Application() {

    /**
     * Foreground-service starts denied by the Android 12+ background-start restriction,
     * retried as soon as the app enters the foreground, where starting is allowed again.
     */
    private val pendingFgsStarts = mutableListOf<Intent>()

    override fun onCreate() {
        super.onCreate()
        app = this
        // Record uncaught exceptions to a local file; the log is offered for
        // local saving on the next launch, never turned into a link.
        HCrash.install()
        // DirtyDataUpdater.update(app)
        if (!HTarget.S) setAppTheme(HailData.appTheme)
        // This process may be created while the app is in the background (watchdog
        // restart, START_STICKY restart, ROM auto-restart, ...), and Android 12+ then
        // forbids starting foreground services: watch for the app entering the
        // foreground so a denied start is retried instead of lost or crashing.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = retryPendingFgsStarts()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        // Package update force-stops the app and cancels START_STICKY restarts,
        // so the service must be restored here as well as on boot.
        if (HailData.swipeFreezeEnabled) {
            setSwipeFreezeService(true)
        }
        if (HailData.lowBatteryShutdown) {
            setLowBatteryShutdownService(true)
        }
    }

    fun setAutoFreezeService() {
        val start = HailData.anyAutoFreezePolicy && HailData.checkedList.any {
            it.packageName != packageName && it.applicationInfo != null && !AppManager.isAppFrozen(it.packageName) && !it.whitelisted
        }
        val intent = Intent(app, AutoFreezeService::class.java)
        if (start) {
            setAutoFreezeServiceEnabled(true)
            startForegroundServiceSafely(intent)
        } else {
            cancelPendingFgsStart(intent)
            stopService(intent)
            setAutoFreezeServiceEnabled(false)
        }
    }

    fun setAutoFreezeServiceEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(app, AutoFreezeService::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun setSwipeFreezeService(enabled: Boolean = HailData.swipeFreezeEnabled) {
        val intent = Intent(this, SwipeFreezeService::class.java)
        if (enabled) startForegroundServiceSafely(intent)
        else {
            cancelPendingFgsStart(intent)
            stopService(intent)
        }
    }

    fun setLowBatteryShutdownService(enabled: Boolean = HailData.lowBatteryShutdown) {
        val intent = Intent(this, LowBatteryShutdownService::class.java)
        if (enabled) startForegroundServiceSafely(intent)
        else {
            cancelPendingFgsStart(intent)
            stopService(intent)
        }
    }

    /**
     * Starts [intent] as a foreground service without ever crashing the process: the
     * Android 12+ background-start restriction (ForegroundServiceStartNotAllowedException)
     * is expected when this process was created in the background, so a denied start is
     * deferred and retried once the app enters the foreground.
     */
    fun startForegroundServiceSafely(intent: Intent): Boolean = try {
        ContextCompat.startForegroundService(this, intent)
        true
    } catch (e: Exception) {
        HLog.w(TAG, "foreground service start deferred (app in background): $intent", e)
        synchronized(pendingFgsStarts) {
            if (pendingFgsStarts.none { it.filterEquals(intent) }) pendingFgsStarts += intent
        }
        false
    }

    private fun retryPendingFgsStarts() {
        synchronized(pendingFgsStarts) {
            val iterator = pendingFgsStarts.iterator()
            while (iterator.hasNext()) {
                val intent = iterator.next()
                try {
                    ContextCompat.startForegroundService(this, intent)
                    iterator.remove()
                } catch (e: Exception) {
                    // Still denied: keep it pending for the next foreground entry.
                    HLog.w(TAG, "foreground service start still deferred: $intent", e)
                }
            }
        }
    }

    /** Drops a deferred start: the service was disabled before it could run. */
    private fun cancelPendingFgsStart(intent: Intent) {
        synchronized(pendingFgsStarts) { pendingFgsStarts.removeAll { it.filterEquals(intent) } }
    }

    fun setAppTheme(theme: String) {
        if (HTarget.S) getSystemService<UiModeManager>()!!.setApplicationNightMode(
            when (theme) {
                HailData.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                HailData.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
        else AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                HailData.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                HailData.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
            }
        )
    }


    companion object {
        lateinit var app: HailApp private set
        private const val TAG = "HailApp"
    }
}
