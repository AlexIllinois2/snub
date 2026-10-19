package io.github.AlexIllinois2.snub.utils

import android.util.Log

object HLog {
    private const val TAG = "Hail"
    fun i(tag: String, string: String) = Log.i(tag, string)
    fun w(tag: String, string: String, t: Throwable? = null) = Log.w(tag, string, t)
    fun e(t: Throwable) = Log.e(TAG, t.stackTraceToString())
}