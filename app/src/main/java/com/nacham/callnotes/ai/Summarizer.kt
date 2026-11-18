package com.nacham.callnotes.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CallInsights(
    val summary: String,
    val actionItems: List<String>,
    val notes: String
)

class Summarizer(private val provider: Provider, private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    fun summarize(transcript: String, contact: String?): CallInsights {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", PROMPT))
            .put(JSONObject().put("role", "user").put("content",
                "Call with: ${contact ?: "unknown"}\n\nTranscript:\n$transcript"))

        val payload = JSONObject()
            .put("model", provider.chatModel)
            .put("temperature", 0.2)
            .put("messages", messages)
            // Both OpenAI and Groq honor JSON mode for these models.
            .put("response_format", JSONObject().put("type", "json_object"))

        val req = Request.Builder()
            .url("${provider.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Summary failed (${resp.code}): $raw")
            val content = JSONObject(raw)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            val o = JSONObject(content)
            val items = mutableListOf<String>()
            o.optJSONArray("action_items")?.let { a ->
                for (i in 0 until a.length()) items.add(a.getString(i))
            }
            return CallInsights(
                summary = o.optString("summary"),
                actionItems = items,
                notes = o.optString("notes")
            )
        }
    }

    companion object {
        val PROMPT = """
            You are an assistant that turns a phone/VoIP call transcript into concise work notes.
            The transcript may be imperfect (speech-to-text). Infer sensibly; never invent facts.
            Return STRICT JSON only, with exactly these keys:
            {
              "summary": "3-6 sentence plain summary of what the call was about and what was decided",
              "action_items": ["clear, imperative todos assigned to the user, each one actionable"],
              "notes": "any dates, numbers, names, commitments, or follow-ups worth keeping"
            }
            If there are no action items, return an empty array. Do not include any text outside the JSON.
        """.trimIndent()
    }
}
