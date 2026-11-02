package com.lashox.huewidget

import android.util.Log

/** Logging that never leaks URLs, entity ids or tokens in release builds. */
internal object HaLog {
    private const val TAG = "HueWidget"

    /** Debug-only message (may contain ids/paths). */
    fun d(msg: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg())
    }

    /** [what] must be a static text (no ids). Release logs only the exception class. */
    fun w(what: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, what, e)
        else Log.w(TAG, if (e == null) what else what + ": " + e.javaClass.simpleName)
    }
}
