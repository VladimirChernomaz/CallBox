package com.callbox.app

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashLog {
    private const val PREFS = "callbox_crash"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, sw.toString()).apply()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun getAndClear(context: Context): String? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = p.getString(KEY, null)
        if (text != null) p.edit().remove(KEY).apply()
        return text
    }
}
