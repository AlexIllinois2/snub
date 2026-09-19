package io.github.AlexIllinois2.snub

import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.services.AutoFreezeService
import io.github.AlexIllinois2.snub.services.LowBatteryShutdownService
import io.github.AlexIllinois2.snub.services.SwipeFreezeService
import io.github.AlexIllinois2.snub.utils.HDhizuku
import io.github.AlexIllinois2.snub.utils.HTarget

class HailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        // DirtyDataUpdater.update(app)
        if (!HTarget.S) setAppTheme(HailData.appTheme)
        if (HailData.workingMode.startsWith(HailData.DHIZUKU)) HDhizuku.init()
        // Package update force-stops the app and cancels START_STICKY restarts,
        // so the service must be restored here as well as on boot.
        if (HailData.swipeFreezeEnabled && HailData.workingMode.startsWith(HailData.SU)) {
            setSwipeFreezeService(true)
        }
        if (HailData.lowBatteryShutdown && HailData.workingMode.startsWith(HailData.SU)) {
            setLowBatteryShutdownService(true)
        }
    }

    fun setAutoFreezeService(autoFreezeAfterLock: Boolean = HailData.autoFreezeAfterLock, context: Context = app) {
        val start = autoFreezeAfterLock && HailData.checkedList.any {
            it.packageName != packageName && it.applicationInfo != null && !AppManager.isAppFrozen(it.packageName) && !it.whitelisted
        }
        val intent = Intent(app, AutoFreezeService::class.java)
        if (start) {
            setAutoFreezeServiceEnabled(true)
            ContextCompat.startForegroundService(context, intent)
        } else {
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
        if (enabled) ContextCompat.startForegroundService(this, intent)
        else stopService(intent)
    }

    fun setLowBatteryShutdownService(enabled: Boolean = HailData.lowBatteryShutdown) {
        val intent = Intent(this, LowBatteryShutdownService::class.java)
        if (enabled) ContextCompat.startForegroundService(this, intent)
        else stopService(intent)
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
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }


    companion object {
        lateinit var app: HailApp private set
    }
}