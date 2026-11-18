package com.nacham.callnotes.ai

import android.content.Context
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.data.RecordingStore
import com.nacham.callnotes.util.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The transcribe -> summarize -> persist pipeline for one recording.
 * Shared by the UI (manual "Transcribe" button) and RecorderService (auto-process after a call).
 */
object Processor {

    /** Runs the full pipeline, updating the store's status as it goes. Safe to call off the main thread. */
    suspend fun process(ctx: Context, id: String) {
        val store = RecordingStore.get(ctx)
        val rec = store.get(id) ?: return
        val key = SettingsStore.apiKey(ctx)
        if (key.isBlank()) {
            store.upsert(rec.copy(status = ProcessStatus.ERROR, error = "Set an API key in Settings"))
            return
        }
        val provider = Provider.from(SettingsStore.provider(ctx))
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
            store.get(id)?.let { store.upsert(it.copy(status = ProcessStatus.ERROR, error = e.message)) }
        }
    }
}
