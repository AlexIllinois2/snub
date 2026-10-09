package io.github.AlexIllinois2.snub.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import java.util.concurrent.TimeUnit

object HWork {
    fun cancelWork(name: String) =
        WorkManager.getInstance(app).cancelUniqueWork(name)

    fun setDeferredFrozen(packageName: String, frozen: Boolean = true, minutes: Long) {
        WorkManager.getInstance(app).enqueueUniqueWork(
            packageName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FrozenWorker>().setInputData(
                workDataOf(
                    HailData.KEY_PACKAGE to packageName,
                    HailData.KEY_FROZEN to frozen
                )
            ).setInitialDelay(minutes, TimeUnit.MINUTES).build()
        )
    }

    fun setAutoFreeze(screenOff: Boolean) {
        val workManager = WorkManager.getInstance(app)
        if (!screenOff) {
            workManager.enqueueUniqueWork(
                HailApi.ACTION_FREEZE_ALL,
                ExistingWorkPolicy.REPLACE,  // in case the old task has not been executed...
                OneTimeWorkRequestBuilder<AutoFreezeWorker>().setInputData(
                    workDataOf(HailData.ACTION_LOCK to false)
                ).build()
            )
            return
        }
        // Screen off: schedule a worker for each tag with the lock policy.
        HailData.tags.filter { it.autoFreezeLock }.forEach { tag ->
            workManager.enqueueUniqueWork(
                "${HailApi.ACTION_LOCK_FREEZE}_${tag.id}",
                ExistingWorkPolicy.REPLACE,  // in case the old task has not been executed...
                OneTimeWorkRequestBuilder<AutoFreezeWorker>()
                    .setInitialDelay(tag.autoFreezeLockDelay.toLong(), TimeUnit.SECONDS)
                    .setInputData(
                        workDataOf(
                            HailData.ACTION_LOCK to true,
                            HailData.KEY_ID to tag.id
                        )
                    ).build()
            )
        }
    }
}