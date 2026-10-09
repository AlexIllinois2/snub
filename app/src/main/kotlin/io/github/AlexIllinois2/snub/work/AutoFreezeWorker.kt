package io.github.AlexIllinois2.snub.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.app.AppInfo
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.services.AutoFreezeService
import io.github.AlexIllinois2.snub.utils.HSystem

class AutoFreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val tagId = inputData.getInt(HailData.KEY_ID, -1)
        if ((inputData.getBoolean(HailData.ACTION_LOCK, true)
                    && HSystem.isInteractive(applicationContext))
            || isSkipWhileCharging(applicationContext)
        ) return Result.success() // Not stopping the AutoFreezeService here. The worker will run at some point. Then we'll stop the Service
        val checkedList = HailData.checkedList.filter {
            (tagId == -1 || it.tagId == tagId) && !isSkipApp(applicationContext, it)
        }
        val result = AppManager.setListFrozen(true, *checkedList.toTypedArray())
        return if (result == null) {
            Result.failure()
        } else {
            app.setAutoFreezeService()
            Result.success()
        }
    }

    private fun isSkipWhileCharging(context: Context): Boolean =
        HailData.skipWhileCharging && HSystem.isCharging(context)

    private fun isSkipApp(context: Context, appInfo: AppInfo): Boolean =
        AppManager.isAppFrozen(appInfo.packageName) || (HailData.skipForegroundApp && HSystem.isForegroundApp(
            context, appInfo.packageName
        )) || (HailData.skipNotifyingApp && AutoFreezeService.instance.activeNotifications.any { it.packageName == appInfo.packageName }) || appInfo.whitelisted
}