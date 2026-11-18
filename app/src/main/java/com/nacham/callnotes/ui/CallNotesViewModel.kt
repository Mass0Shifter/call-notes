package com.nacham.callnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nacham.callnotes.ai.Provider
import com.nacham.callnotes.ai.Summarizer
import com.nacham.callnotes.ai.Transcriber
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.data.Recording
import com.nacham.callnotes.data.RecordingStore
import com.nacham.callnotes.util.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CallNotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecordingStore.get(app)
    val items = store.items

    /** Full pipeline for one recording: transcribe -> summarize -> persist, with status updates. */
    fun process(id: String) {
        val ctx = getApplication<Application>()
        val rec = store.get(id) ?: return
        val key = SettingsStore.apiKey(ctx)
        if (key.isBlank()) {
            store.upsert(rec.copy(status = ProcessStatus.ERROR, error = "Set an API key in Settings"))
            return
        }
        val provider = Provider.from(SettingsStore.provider(ctx))
        viewModelScope.launch {
            try {
                store.upsert(rec.copy(status = ProcessStatus.TRANSCRIBING, error = null))
                val transcript = withContext(Dispatchers.IO) {
                    Transcriber(provider, key).transcribe(File(rec.filePath))
                }
                store.upsert(store.get(id)!!.copy(transcript = transcript, status = ProcessStatus.SUMMARIZING))
                val insights = withContext(Dispatchers.IO) {
                    Summarizer(provider, key).summarize(transcript, rec.contact)
                }
                store.upsert(
                    store.get(id)!!.copy(
                        summary = insights.summary,
                        actionItems = insights.actionItems,
                        notes = insights.notes,
                        status = ProcessStatus.DONE
                    )
                )
            } catch (e: Exception) {
                store.get(id)?.let {
                    store.upsert(it.copy(status = ProcessStatus.ERROR, error = e.message))
                }
            }
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
