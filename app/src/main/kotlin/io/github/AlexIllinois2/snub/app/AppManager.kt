package io.github.AlexIllinois2.snub.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManager {
    val lockScreen: Boolean
        get() = HShell.lockScreen

    fun isAppFrozen(info: ApplicationInfo): Boolean = when {
        HailData.workingMode.endsWith(HailData.STOP) -> HPackages.isAppStopped(info)
        HailData.workingMode.endsWith(HailData.HIDE) -> HPackages.isAppHidden(info)
        HailData.workingMode.endsWith(HailData.SUSPEND) -> HPackages.isAppSuspended(info)
        else -> HPackages.isAppDisabled(info)
    }

    fun isAppFrozen(packageName: String): Boolean = when {
        HailData.workingMode.endsWith(HailData.STOP) -> HPackages.isAppStopped(packageName)
        HailData.workingMode.endsWith(HailData.HIDE) -> HPackages.isAppHidden(packageName)
        HailData.workingMode.endsWith(HailData.SUSPEND) -> HPackages.isAppSuspended(packageName)
        else -> HPackages.isAppDisabled(packageName)
    }

    fun setListFrozen(frozen: Boolean, vararg appInfo: AppInfo): String? {
        val excludeMe = appInfo.filter { it.packageName != BuildConfig.APPLICATION_ID }
        var i = 0
        var denied = false
        var name = String()
        excludeMe.forEach {
            when {
                setAppFrozen(it.packageName, frozen) -> {
                    i++
                    name = it.name.toString()
                }

                it.applicationInfo != null -> denied = true
            }
        }
        return if (denied && i == 0) null else if (i == 1) name else i.toString()
    }

    fun setAppFrozen(packageName: String, frozen: Boolean): Boolean {
        if (packageName == BuildConfig.APPLICATION_ID) return false
        val success = when (HailData.workingMode) {
            HailData.MODE_SU_STOP -> !frozen || HShell.forceStopApp(packageName)
            HailData.MODE_SU_DISABLE -> HShell.setAppDisabled(packageName, frozen)
            HailData.MODE_SU_HIDE -> HShell.setAppHidden(packageName, frozen)
            HailData.MODE_SU_SUSPEND -> HShell.setAppSuspended(packageName, frozen)
            else -> false
        }
        if (success) when {
            frozen -> TempUnfrozenList.remove(packageName)
            HailData.isChecked(packageName) && !HailData.isWhitelisted(packageName) ->
                TempUnfrozenList.add(packageName)
        }
        return success
    }

    fun uninstallApp(packageName: String): Boolean {
        if (HShell.uninstallApp(packageName)) return true
        HUI.startActivity(Intent.ACTION_DELETE, HPackages.packageUri(packageName))
        return false
    }

    fun reinstallApp(packageName: String): Boolean = HShell.reinstallApp(packageName)

    suspend fun execute(command: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        HShell.execute(command, true)
    }
}