package io.github.AlexIllinois2.snub.utils

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.AppInfo
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader

object HShortcuts {
    private val iconLoader by lazy {
        AppIconLoader(
            app.resources.getDimensionPixelSize(R.dimen.app_icon_size),
            HailData.synthesizeAdaptiveIcons,
            app
        )
    }

    fun addPinShortcut(icon: Drawable, id: String, label: CharSequence, intent: Intent) {
        addPinShortcut(getDrawableIcon(icon), id, label, intent)
    }

    fun addPinShortcut(appInfo: AppInfo, id: String, label: CharSequence, intent: Intent) {
        appInfo.applicationInfo?.let {
            val icon = IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it)
            addPinShortcut(IconCompat.createWithBitmap(icon), id, label, intent)
        } ?: run {
            addPinShortcut(app.packageManager.defaultActivityIcon, id, label, intent)
        }
    }

    private fun addPinShortcut(icon: IconCompat, id: String, label: CharSequence, intent: Intent) {
        if (!requestPinShortcut(icon, id, label, intent)) HUI.showToast(
            R.string.operation_failed, app.getString(R.string.action_add_pin_shortcut)
        )
    }

    /**
     * Requests the launcher to pin the shortcut.
     * @return whether the request was submitted to the launcher;
     * whether the user confirmed it afterwards is unknown to the system.
     */
    fun requestPinShortcut(icon: IconCompat, id: String, label: CharSequence, intent: Intent): Boolean =
        runCatching {
            ShortcutManagerCompat.isRequestPinShortcutSupported(app) && ShortcutManagerCompat.requestPinShortcut(
                app,
                ShortcutInfoCompat.Builder(app, id).setIcon(icon).setShortLabel(label).setIntent(intent).build(),
                null
            )
        }.getOrDefault(false)

    /**
     * Requests pin shortcuts that unfreeze and launch apps, one by one, calling [awaitNext]
     * after each submission, so that the launcher's confirmation dialog for the current
     * request gets handled before the next one replaces it.
     * Uninstalled apps are skipped; aborts when [shouldContinue] returns false
     * or the launcher rejects a request (e.g. no home screen app).
     *
     * @return the number of submitted requests.
     */
    suspend fun requestBatchPinShortcuts(
        apps: List<AppInfo>,
        onProgress: (requested: Int, total: Int) -> Unit,
        awaitNext: suspend () -> Unit,
        shouldContinue: () -> Boolean
    ): Int {
        var requested = 0
        for (appInfo in apps) {
            if (!shouldContinue()) break
            val pkg = appInfo.packageName
            val applicationInfo = appInfo.applicationInfo ?: continue // Ghost data of uninstalled apps
            val submitted = withContext(Dispatchers.Default) {
                val icon = IconPack.loadIcon(pkg) ?: iconLoader.loadIcon(applicationInfo)
                requestPinShortcut(
                    IconCompat.createWithBitmap(icon), pkg, appInfo.name,
                    HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                )
            }
            if (!submitted) break
            onProgress(++requested, apps.size)
            awaitNext()
        }
        return requested
    }

    private val silentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val silentMutex = Mutex()

    /**
     * Silently creates a home screen shortcut for a newly managed app,
     * if the corresponding option is enabled and no shortcut has been created for it yet.
     */
    fun autoCreateSilentShortcut(packageName: String) {
        if (!HailData.autoShortcutNewApps || packageName == BuildConfig.APPLICATION_ID) return
        if (hasSilentShortcut(packageName)) return
        silentScope.launch { createSilentShortcut(packageName) }
    }

    /**
     * Whether a home screen shortcut has been created for the package,
     * either silently via [createSilentShortcut] or by a confirmed pin request
     * (per-app pin shortcuts use the package name as their id).
     */
    fun hasSilentShortcut(packageName: String): Boolean =
        HailData.isShortcutCreated(packageName) || runCatching {
            ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .any { it.id == packageName }
        }.getOrDefault(false)

    /**
     * Sends the legacy INSTALL_SHORTCUT broadcast, to which launchers supporting it respond
     * by placing the shortcut on the home screen silently, without a confirmation dialog.
     * Whether the launcher actually handled the broadcast is not observable;
     * the package is recorded as having a shortcut regardless.
     *
     * @return whether the broadcast was sent.
     */
    suspend fun createSilentShortcut(packageName: String): Boolean = withContext(Dispatchers.Default) {
        silentMutex.withLock {
            val applicationInfo = HPackages.getApplicationInfoOrNull(packageName)
                ?: return@withContext false // Ghost data of uninstalled apps
            runCatching {
                // Intent.ACTION_INSTALL_SHORTCUT was removed in API 36; the constant value is unchanged.
                app.sendBroadcast(Intent("android.intent.action.INSTALL_SHORTCUT").apply {
                    putExtra(
                        Intent.EXTRA_SHORTCUT_INTENT,
                        HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, packageName)
                    )
                    putExtra(
                        Intent.EXTRA_SHORTCUT_NAME,
                        applicationInfo.loadLabel(app.packageManager).toString()
                    )
                    putExtra(
                        Intent.EXTRA_SHORTCUT_ICON,
                        IconPack.loadIcon(packageName) ?: iconLoader.loadIcon(applicationInfo)
                    )
                    putExtra("duplicate", false) // Avoid duplicated icons where supported
                })
                HailData.addShortcutCreated(packageName)
            }.isSuccess
        }
    }

    /**
     * Silently creates home screen shortcuts for [apps], one by one.
     * @return the number of broadcasts sent.
     */
    suspend fun createSilentShortcuts(apps: List<AppInfo>): Int {
        var created = 0
        for (appInfo in apps) {
            if (createSilentShortcut(appInfo.packageName)) created++
        }
        return created
    }

    fun addDynamicShortcut(packageName: String) {
        if (HailData.biometricLogin) return
        val applicationInfo = HPackages.getApplicationInfoOrNull(packageName)
        val shortcut =
            ShortcutInfoCompat.Builder(app, packageName.hashCode().toString()) // Make id different from pin
                .setIcon(IconCompat.createWithBitmap(applicationInfo?.let {
                    IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it)
                } ?: getBitmapFromDrawable(
                    app.packageManager.defaultActivityIcon
                ))).setShortLabel(
                    applicationInfo?.loadLabel(app.packageManager) ?: packageName
                ).setIntent(HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, packageName)).build()
        ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
        addDynamicShortcutAction(HailData.dynamicShortcutAction)
    }

    fun addDynamicShortcutAction(action: String) {
        if (action == HailData.ACTION_NONE) return
        val id = when (action) {
            HailData.ACTION_FREEZE_ALL -> HailApi.ACTION_FREEZE_ALL
            HailData.ACTION_FREEZE_NON_WHITELISTED -> HailApi.ACTION_FREEZE_NON_WHITELISTED
            HailData.ACTION_LOCK -> HailApi.ACTION_LOCK
            HailData.ACTION_LOCK_FREEZE -> HailApi.ACTION_LOCK_FREEZE
            else -> HailApi.ACTION_UNFREEZE_ALL
        }
        val icon = when (action) {
            HailData.ACTION_FREEZE_ALL, HailData.ACTION_FREEZE_NON_WHITELISTED -> R.drawable.ic_round_frozen_shortcut
            HailData.ACTION_LOCK, HailData.ACTION_LOCK_FREEZE -> R.drawable.ic_outline_lock_shortcut
            else -> R.drawable.ic_round_unfrozen_shortcut
        }
        val label = when (action) {
            HailData.ACTION_FREEZE_ALL -> R.string.action_freeze_all
            HailData.ACTION_FREEZE_NON_WHITELISTED -> R.string.action_freeze_non_whitelisted
            HailData.ACTION_LOCK -> R.string.action_lock
            HailData.ACTION_LOCK_FREEZE -> R.string.action_lock_freeze
            else -> R.string.action_unfreeze_all
        }
        val shortcut = ShortcutInfoCompat.Builder(app, id).setIcon(
            getDrawableIcon(
                AppCompatResources.getDrawable(
                    app, icon
                )!!
            )
        ).setShortLabel(app.getString(label)).setIntent(Intent(id)).build()
        ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
    }

    fun removeAllDynamicShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(app)
    }

    private fun getDrawableIcon(drawable: Drawable): IconCompat =
        IconCompat.createWithBitmap(getBitmapFromDrawable(drawable))

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
    ).also {
        with(Canvas(it)) {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
    }
}