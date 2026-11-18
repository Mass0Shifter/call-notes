package com.nacham.callnotes.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nacham.callnotes.ai.Processor
import com.nacham.callnotes.audio.AudioStore
import com.nacham.callnotes.call.CallImport
import com.nacham.callnotes.call.CallSource
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.data.Recording
import com.nacham.callnotes.data.RecordingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CallNotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecordingStore.get(app)
    val items = store.items

    /** Full pipeline for one recording: transcribe -> summarize -> persist. */
    fun process(id: String) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch { Processor.process(ctx, id) }
    }

    /** Scan MediaStore for the phone's own call recordings (Samsung) and import new ones. */
    fun importSamsungRecordings(onDone: (Int) -> Unit) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { CallImport.scan(ctx) }.getOrDefault(0) }
            onDone(n)
        }
    }

    /** Import an audio file shared into the app from elsewhere; returns the new recording id. */
    fun importUri(uri: Uri, onDone: (String?) -> Unit) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(AudioStore.dir(ctx), "shared_${System.currentTimeMillis()}.m4a")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    val newId = UUID.randomUUID().toString()
                    store.upsert(
                        Recording(
                            id = newId,
                            filePath = dest.absolutePath,
                            source = CallSource.MANUAL.name,
                            contact = "Imported audio",
                            startedAt = System.currentTimeMillis(),
                            status = ProcessStatus.RAW
                        )
                    )
                    newId
                }.getOrNull()
            }
            onDone(id)
        }
    }

    fun delete(id: String) = store.delete(id)

    fun get(id: String): Recording? = store.get(id)

    /** Build a shareable markdown block for export to notes/docs/email. */
    fun exportMarkdown(rec: Recording): String = buildString {
        appendLine("# ${rec.contact ?: "Call"} — ${java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rec.startedAt))}")
        appendLine()
        rec.summary?.let { appendLine("## Summary"); appendLine(it); appendLine() }
        if (rec.actionItems.isNotEmpty()) {
            appendLine("## Action items")
            rec.actionItems.forEach { appendLine("- [ ] $it") }
            appendLine()
        }
        rec.notes?.takeIf { it.isNotBlank() }?.let { appendLine("## Notes"); appendLine(it); appendLine() }
        rec.transcript?.let { appendLine("## Transcript"); appendLine(it) }
    }
}
