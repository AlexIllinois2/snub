package io.github.AlexIllinois2.snub.work

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.HailApp
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.utils.HPackages

/**
 * Adds every user app installed after [HailData.autoManageSince] to management.
 * Covers the installs missed by [io.github.AlexIllinois2.snub.receiver.ManageNewAppsReceiver]
 * while the process was dead; it runs whenever the process is created.
 */
class ManageNewAppsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val since = HailData.autoManageSince
        if (since == 0L) return Result.success()
        val added = HPackages.getInstalledPackages().filter { packageInfo ->
            val info = packageInfo.applicationInfo
            packageInfo.packageName != BuildConfig.APPLICATION_ID && info != null
                    && info.flags and ApplicationInfo.FLAG_INSTALLED != 0
                    && info.flags and ApplicationInfo.FLAG_SYSTEM == 0
                    && info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                    && packageInfo.firstInstallTime > since
                    && !HailData.isChecked(packageInfo.packageName)
        }.map { it.packageName }
        if (added.isEmpty()) return Result.success()
        added.forEach { HailData.addCheckedApp(it) }
        HailApp.app.setAutoFreezeService()
        return Result.success()
    }
}
