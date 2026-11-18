package com.nacham.callnotes.util

import android.content.Context

/**
 * Simple SharedPreferences-backed settings (synchronous reads are convenient for services).
 * TODO(handoff): migrate to DataStore if you prefer flows in the UI; the dependency is included.
 */
object SettingsStore {
    private const val PREFS = "callnotes_settings"
    private const val K_ENABLED = "enabled"
    private const val K_PROVIDER = "provider"
    private const val K_API_KEY = "api_key"
    private const val K_AUTO = "auto_process"
    private const val K_ANNOUNCE = "announce_recording"
    private const val K_CELLULAR_MIC = "cellular_mic"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = p(ctx).getBoolean(K_ENABLED, true)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_ENABLED, v).apply()

    fun provider(ctx: Context) = p(ctx).getString(K_PROVIDER, "GROQ") ?: "GROQ"
    fun setProvider(ctx: Context, v: String) = p(ctx).edit().putString(K_PROVIDER, v).apply()

    fun apiKey(ctx: Context) = p(ctx).getString(K_API_KEY, "") ?: ""
    fun setApiKey(ctx: Context, v: String) = p(ctx).edit().putString(K_API_KEY, v).apply()

    fun autoProcess(ctx: Context) = p(ctx).getBoolean(K_AUTO, false)
    fun setAutoProcess(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_AUTO, v).apply()

    fun announceRecording(ctx: Context) = p(ctx).getBoolean(K_ANNOUNCE, false)
    fun setAnnounceRecording(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_ANNOUNCE, v).apply()

    fun recordCellularMic(ctx: Context) = p(ctx).getBoolean(K_CELLULAR_MIC, false)
    fun setRecordCellularMic(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_CELLULAR_MIC, v).apply()
}
