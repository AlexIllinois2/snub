package io.github.AlexIllinois2.snub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.utils.HShell
import io.github.AlexIllinois2.snub.utils.HTarget

class UnsuspendedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PACKAGE_UNSUSPENDED_MANUALLY) runCatching {
            if (HTarget.P) HShell.setAppRestricted(
                intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)!!,
                false
            )
            app.setAutoFreezeService()
        }
    }

    companion object {
        private const val ACTION_PACKAGE_UNSUSPENDED_MANUALLY =
            "android.intent.action.PACKAGE_UNSUSPENDED_MANUALLY"
    }
}