package io.github.AlexIllinois2.snub.utils

import android.os.Build
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a local file. No link is ever generated or uploaded:
 * the log is only offered to the user for saving to local storage on the next launch,
 * and is discarded if the user declines.
 */
object HCrash {
    private val dir: File get() = File(app.filesDir, "crash")

    /** Installs the handler; safe to call once per process from [io.github.AlexIllinois2.snub.HailApp]. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let log recording mask the original crash.
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(thread: Thread, throwable: Throwable): File {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log")
        file.writeText(buildString {
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(throwable.stackTraceToString())
        })
        return file
    }

    /** Logs that have not been offered to the user yet, oldest first. */
    fun pendingLogs(): List<File> =
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(OFFERED_SUFFIX) }?.sortedBy { it.lastModified() }
            ?: emptyList()

    /** Marks a log as offered so it is not prompted for again; keeps the file on disk. */
    fun markOffered(file: File) {
        runCatching { file.renameTo(File(dir, file.name + OFFERED_SUFFIX)) }
    }

    /** Deletes every recorded log, offered or not. */
    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private const val OFFERED_SUFFIX = ".offered"
}
