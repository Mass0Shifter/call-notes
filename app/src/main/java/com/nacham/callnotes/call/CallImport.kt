package com.nacham.callnotes.call

import android.content.Context
import android.provider.MediaStore
import com.nacham.callnotes.audio.AudioStore
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.data.Recording
import com.nacham.callnotes.data.RecordingStore
import java.io.File
import java.util.UUID

/**
 * Imports the device's own call recordings (Samsung writes these to a "Call" folder when the
 * built-in call recorder is on). This is the reliable way to get the OTHER party's audio on
 * cellular calls. Requires READ_MEDIA_AUDIO.
 */
object CallImport {

    /** Returns the number of newly imported recordings. */
    fun scan(ctx: Context): Int {
        val store = RecordingStore.get(ctx)
        val known = store.items.value.map { File(it.filePath).name }.toSet()
        var imported = 0

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION
        )
        // Match common call-recording locations across Samsung/OEM builds.
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%Call%", "%call_rec%", "%Call%")

        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val dest = File(AudioStore.dir(ctx), "import_$name")
                if (dest.name in known || dest.exists()) continue

                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)
                )
                val ok = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
                if (!ok || !dest.exists()) continue

                store.upsert(
                    Recording(
                        id = UUID.randomUUID().toString(),
                        filePath = dest.absolutePath,
                        source = CallSource.CELLULAR.name,
                        contact = contactFromName(name),
                        startedAt = c.getLong(dateCol) * 1000L,
                        durationMs = c.getLong(durCol),
                        status = ProcessStatus.RAW
                    )
                )
                imported++
            }
        }
        return imported
    }

    /** Samsung names files like "Call recording <Name>_<date>.m4a" — pull a readable label. */
    private fun contactFromName(name: String): String {
        val base = name.substringBeforeLast('.')
            .replace("Call recording", "", ignoreCase = true)
            .replace("call_rec", "", ignoreCase = true)
            .trim('_', ' ', '-')
        return base.ifBlank { "Imported call" }
    }
}
