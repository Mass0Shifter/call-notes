package com.nacham.callnotes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Lightweight JSON-file persistence. Single source of truth for the recordings list.
 * Not Room, deliberately — keeps the build dependency-free and the handoff simple.
 */
class RecordingStore private constructor(private val file: File) {

    private val _items = MutableStateFlow<List<Recording>>(emptyList())
    val items: StateFlow<List<Recording>> = _items.asStateFlow()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        if (!file.exists()) { _items.value = emptyList(); return }
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: JSONArray()
        val list = (0 until arr.length()).map { Recording.fromJson(arr.getJSONObject(it)) }
        _items.value = list.sortedByDescending { it.startedAt }
    }

    @Synchronized
    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    @Synchronized
    fun upsert(rec: Recording) {
        val map = _items.value.associateBy { it.id }.toMutableMap()
        map[rec.id] = rec
        _items.value = map.values.sortedByDescending { it.startedAt }
        persist()
    }

    fun get(id: String): Recording? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun delete(id: String) {
        get(id)?.let { runCatching { File(it.filePath).delete() } }
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    companion object {
        @Volatile private var INSTANCE: RecordingStore? = null
        fun get(ctx: Context): RecordingStore = INSTANCE ?: synchronized(this) {
            INSTANCE ?: RecordingStore(File(ctx.filesDir, "recordings.json")).also { INSTANCE = it }
        }
    }
}
