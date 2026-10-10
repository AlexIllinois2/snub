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
    private const val TAG_HELPER = "HShortcuts-root"

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

    /** Whether the current working mode grants root, allowing [RootShortcutSender] to be used. */
    private val hasRootAccess: Boolean
        get() = HailData.workingMode.startsWith(HailData.SU) ||
                (HailData.workingMode.startsWith(HailData.SHIZUKU) &&
                        runCatching { HShizuku.isRoot }.getOrDefault(false))

    /**
     * Sends the INSTALL_SHORTCUT broadcasts for [packages] as root via [RootShortcutSender],
     * bypassing the system-side drop for O+ targeting senders and launcher per-app permissions.
     * @return the packages whose broadcasts were delivered; an empty list when root was available
     * but the helper failed (reported as 0, never falling back to a broadcast that the system
     * drops anyway); or null when root is unavailable, meaning the caller should send as the app.
     */
    private fun sendRootShortcuts(packages: List<String>): List<String>? {
        if (packages.isEmpty() || !hasRootAccess) return null
        val apkPath = app.applicationInfo.sourceDir ?: return null
        val command = "env CLASSPATH=$apkPath app_process /system --nice-name=snub_shortcut " +
                RootShortcutSender::class.java.name + " " + packages.joinToString(" ")
        val (_, output) = when {
            HailData.workingMode.startsWith(HailData.SU) -> HShell.execute(command, true)
            else -> HShizuku.execute(command, true)
        }
        val delivered = output?.lineSequence()?.mapNotNull { line ->
            if (line.startsWith("OK ")) line.removePrefix("OK ").trim().takeIf { it.isNotEmpty() } else null
        }?.toList().orEmpty()
        if (delivered.size < packages.size) HLog.i(TAG_HELPER, output ?: "no output, exit without OK lines")
        return delivered
    }

    /**
     * Silently creates a home screen shortcut for a newly managed app,
     * if the corresponding option is enabled and it has no pinned shortcut yet.
     */
    fun autoCreateSilentShortcut(packageName: String) {
        if (!HailData.autoShortcutNewApps || packageName == BuildConfig.APPLICATION_ID) return
        if (hasPinnedShortcut(packageName)) return
        silentScope.launch { createSilentShortcut(packageName) }
    }

    /**
     * Whether a pinned shortcut has been created for the package
     * (per-app pin shortcuts use the package name as their id).
     * Shortcuts created via the silent broadcast are not tracked here:
     * launchers deduplicate them via the "duplicate" extra when delivering.
     */
    fun hasPinnedShortcut(packageName: String): Boolean = runCatching {
        ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == packageName }
    }.getOrDefault(false)

    /**
     * Silently creates a home screen shortcut for [packageName],
     * preferring the root-assisted delivery via [sendRootShortcuts].
     * Whether the launcher actually handled the broadcast is not observable.
     *
     * @return whether the broadcast was delivered as root, or sent as the app otherwise.
     */
    suspend fun createSilentShortcut(packageName: String): Boolean = withContext(Dispatchers.Default) {
        silentMutex.withLock { createSilentShortcutLocked(packageName) }
    }

    private suspend fun createSilentShortcutLocked(packageName: String): Boolean {
        val applicationInfo = HPackages.getApplicationInfoOrNull(packageName)
            ?: return false // Ghost data of uninstalled apps
        val sentViaRoot = sendRootShortcuts(listOf(packageName))
        if (sentViaRoot != null) return packageName in sentViaRoot
        return runCatching {
            // Intent.ACTION_INSTALL_SHORTCUT was removed in API 36; the constant value is unchanged.
            // Since Android 8.0 the system silently drops this broadcast from apps targeting O+,
            // so the root-assisted path above is required on modern launchers.
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
        }.isSuccess
    }

    /**
     * Silently creates home screen shortcuts for [apps], one by one,
     * or in a single root helper run when root is available.
     * Launchers deduplicate already existing shortcuts via the "duplicate" extra.
     * @return the apps whose broadcasts were not verifiably delivered,
     * so the caller can offer interactive adding for them.
     */
    suspend fun createSilentShortcuts(apps: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.Default) {
        silentMutex.withLock {
            val packages = apps.map { it.packageName }
            val sentViaRoot = sendRootShortcuts(packages)
            if (sentViaRoot != null) apps.filter { it.packageName !in sentViaRoot }
            else {
                apps.forEach { createSilentShortcutLocked(it.packageName) }
                emptyList() // Delivery of the plain broadcast is not observable
            }
        }
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