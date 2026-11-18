package com.nacham.callnotes.audio

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioStore {
    /** App-private external dir; survives app updates, user-visible via Files > Android/data. */
    fun dir(ctx: Context): File =
        (ctx.getExternalFilesDir("recordings") ?: File(ctx.filesDir, "recordings")).apply { mkdirs() }

    fun newFile(ctx: Context, source: String): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(ctx), "call_${source.lowercase()}_$ts.m4a")
    }
}
