package com.nacham.callnotes.data

import org.json.JSONArray
import org.json.JSONObject

enum class ProcessStatus { RAW, TRANSCRIBING, TRANSCRIBED, SUMMARIZING, DONE, ERROR }

data class Recording(
    val id: String,
    val filePath: String,
    val source: String,            // CallSource name
    val contact: String? = null,
    val startedAt: Long,
    val durationMs: Long = 0,
    val transcript: String? = null,
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val notes: String? = null,
    val status: ProcessStatus = ProcessStatus.RAW,
    val error: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        put("source", source)
        put("contact", contact ?: JSONObject.NULL)
        put("startedAt", startedAt)
        put("durationMs", durationMs)
        put("transcript", transcript ?: JSONObject.NULL)
        put("summary", summary ?: JSONObject.NULL)
        put("actionItems", JSONArray(actionItems))
        put("notes", notes ?: JSONObject.NULL)
        put("status", status.name)
        put("error", error ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): Recording {
            val items = mutableListOf<String>()
            o.optJSONArray("actionItems")?.let { a ->
                for (i in 0 until a.length()) items.add(a.getString(i))
            }
            return Recording(
                id = o.getString("id"),
                filePath = o.getString("filePath"),
                source = o.getString("source"),
                contact = o.optString("contact").takeIf { it.isNotBlank() && it != "null" },
                startedAt = o.getLong("startedAt"),
                durationMs = o.optLong("durationMs"),
                transcript = o.optString("transcript").takeIf { it.isNotBlank() && it != "null" },
                summary = o.optString("summary").takeIf { it.isNotBlank() && it != "null" },
                actionItems = items,
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                status = runCatching { ProcessStatus.valueOf(o.optString("status", "RAW")) }
                    .getOrDefault(ProcessStatus.RAW),
                error = o.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
