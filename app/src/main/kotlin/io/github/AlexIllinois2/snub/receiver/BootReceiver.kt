package io.github.AlexIllinois2.snub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.services.LowBatteryShutdownService
import io.github.AlexIllinois2.snub.services.SwipeFreezeService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!HailData.workingMode.startsWith(HailData.SU)) return
        if (HailData.swipeFreezeEnabled) ContextCompat.startForegroundService(
            context, Intent(context, SwipeFreezeService::class.java)
        )
        if (HailData.lowBatteryShutdown) ContextCompat.startForegroundService(
            context, Intent(context, LowBatteryShutdownService::class.java)
        )
    }
}
