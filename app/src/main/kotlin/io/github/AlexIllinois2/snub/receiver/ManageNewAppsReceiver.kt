package io.github.AlexIllinois2.snub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.app.HailData

/**
 * Runtime-registered by [io.github.AlexIllinois2.snub.HailApp] while
 * "auto manage newly installed apps" is enabled:
 * a manifest receiver cannot receive ACTION_PACKAGE_ADDED on API 26+.
 */
class ManageNewAppsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!HailData.autoManageNewApps) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // Update, not a fresh install
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == BuildConfig.APPLICATION_ID || HailData.isChecked(packageName)) return
        HailData.addCheckedApp(packageName)
    }
}
