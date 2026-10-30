package io.github.AlexIllinois2.snub.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Looper
import android.util.Log
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import kotlin.system.exitProcess

/**
 * Standalone app_process entry that sends the legacy INSTALL_SHORTCUT broadcast as root.
 *
 * On API 26+ the system silently drops this broadcast when sent by apps targeting O+,
 * and launchers may additionally gate it behind per-app permissions (e.g. MIUI, ColorOS);
 * a root process (uid 0) bypasses all of these. Invoked via:
 *
 * `env CLASSPATH=<apk> app_process /system --nice-name=snub_shortcut <class> <package>...`
 *
 * Prints `OK <package>` per broadcast delivered, `FAIL <package> <reason>` per rejected one,
 * or `FATAL <reason>` if the root context could not be initialized.
 *
 * Must stay self-contained: only android.* APIs and compile-time constants (inlined,
 * e.g. [HailApi.ACTION_LAUNCH]) are referenced here — objects depending on an
 * Application instance (HailApp, HailData as an object, HShortcuts) must not be touched.
 */
object RootShortcutSender {

    @JvmStatic
    fun main(args: Array<String>) {
        val context = runCatching {
            // ActivityThread's constructor instantiates its H handler,
            // which requires a Looper prepared on the current thread
            Looper.prepareMainLooper()
            val thread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null)
            thread.javaClass.getMethod("getSystemContext").invoke(thread) as Context
        }.getOrElse {
            println("FATAL ${Log.getStackTraceString(it)}")
            exitProcess(1)
        }
        val pm = context.packageManager
        for (pkg in args) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrDefault(pm.defaultActivityIcon)
                context.sendBroadcast(Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                    // Keep in sync with HShortcuts: launch through Snub so frozen apps get unfrozen first
                    putExtra(
                        Intent.EXTRA_SHORTCUT_INTENT,
                        Intent(HailApi.ACTION_LAUNCH).putExtra(HailData.KEY_PACKAGE, pkg)
                    )
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, info.loadLabel(pm).toString())
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, icon.toBitmap())
                    putExtra("duplicate", false) // Avoid duplicated icons where supported
                })
                println("OK $pkg")
            } catch (e: Throwable) {
                println("FAIL $pkg ${e.message?.replace('\n', ' ')}")
            }
        }
    }

    private fun Drawable.toBitmap(): Bitmap {
        val width = intrinsicWidth.coerceAtLeast(1)
        val height = intrinsicHeight.coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            setBounds(0, 0, width, height)
            draw(Canvas(it))
        }
    }
}
