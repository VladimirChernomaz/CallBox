package com.callbox.app

import android.content.Context

object Breadcrumb {
    private const val PREFS = "callbox_crash"
    private const val KEY = "last_breadcrumb"

    /** Writes synchronously (commit, not apply) so this survives even a native crash. */
    fun mark(context: Context, label: String) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, label).commit()
        } catch (_: Throwable) {
        }
    }

    fun getAndClear(context: Context): String? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = p.getString(KEY, null)
        if (text != null) p.edit().remove(KEY).apply()
        return text
    }
}
