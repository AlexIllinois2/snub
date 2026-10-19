package io.github.AlexIllinois2.snub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.services.LowBatteryShutdownService
import io.github.AlexIllinois2.snub.services.SwipeFreezeService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // BOOT_COMPLETED is normally exempt from the background FGS-start restriction,
        // but some ROMs deny it anyway: start defensively so a denial defers the start
        // to the app's next foreground entry instead of crashing.
        if (HailData.swipeFreezeEnabled) app.startForegroundServiceSafely(
            Intent(context, SwipeFreezeService::class.java)
        )
        if (HailData.lowBatteryShutdown) app.startForegroundServiceSafely(
            Intent(context, LowBatteryShutdownService::class.java)
        )
    }
}
