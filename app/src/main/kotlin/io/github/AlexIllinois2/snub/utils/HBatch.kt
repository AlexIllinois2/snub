package io.github.AlexIllinois2.snub.utils

import android.app.Activity
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.AlexIllinois2.snub.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs a slow batch operation with a live progress dialog, so that freezing or
 * unfreezing many apps one by one does not look like the UI is frozen.
 * [operation] runs sequentially on the caller's dispatcher; the loop stops before
 * the next item when the user cancels the dialog or the caller's scope is cancelled.
 */
object HBatch {
    data class Result(val succeeded: Int, val failed: Int, val lastSucceeded: CharSequence = "")

    /** Small batches run silently: a dialog would cost more than the operation itself. */
    private const val PROGRESS_THRESHOLD = 3

    suspend fun <T> run(
        activity: Activity,
        items: List<T>,
        @StringRes title: Int,
        label: (T) -> CharSequence,
        operation: (T) -> Boolean
    ): Result {
        if (items.size <= PROGRESS_THRESHOLD || activity.isFinishing) {
            var succeeded = 0
            var last: CharSequence = ""
            items.forEach {
                if (operation(it)) {
                    succeeded++
                    last = label(it)
                }
            }
            return Result(succeeded, items.size - succeeded, last)
        }
        val cancelled = AtomicBoolean(false)
        val view = LayoutInflater.from(activity).inflate(R.layout.view_batch_progress, null)
        val current = view.findViewById<TextView>(R.id.batch_current)
        val bar = view.findViewById<LinearProgressIndicator>(R.id.batch_bar)
        val count = view.findViewById<TextView>(R.id.batch_count)
        bar.max = items.size
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()
        var succeeded = 0
        var last: CharSequence = ""
        try {
            for ((index, item) in items.withIndex()) {
                if (cancelled.get()) break
                withContext(Dispatchers.Main) {
                    current.text = label(item)
                    bar.setProgressCompat(index, true)
                    count.text = "${index + 1}/${items.size}"
                }
                if (operation(item)) {
                    succeeded++
                    last = label(item)
                }
            }
        } finally {
            withContext(Dispatchers.Main + NonCancellable) { runCatching { dialog.dismiss() } }
        }
        return Result(succeeded, items.size - succeeded, last)
    }
}
