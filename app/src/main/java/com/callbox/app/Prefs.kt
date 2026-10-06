package com.callbox.app

import android.content.Context

object Prefs {
    private const val NAME = "callbox_prefs"
    private const val KEY_PUBLISHABLE = "publishable_key"
    private const val KEY_CHANNEL = "channel"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun hasConfig(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString(KEY_PUBLISHABLE, "").isNullOrBlank() &&
            !p.getString(KEY_CHANNEL, "").isNullOrBlank()
    }

    fun getPublishableKey(context: Context): String =
        prefs(context).getString(KEY_PUBLISHABLE, "") ?: ""

    fun getChannel(context: Context): String =
        prefs(context).getString(KEY_CHANNEL, "") ?: ""

    fun save(context: Context, publishableKey: String, channel: String) {
        prefs(context).edit()
            .putString(KEY_PUBLISHABLE, publishableKey.trim())
            .putString(KEY_CHANNEL, channel.trim())
            .apply()
    }
}
