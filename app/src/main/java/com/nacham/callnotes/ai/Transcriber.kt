package com.nacham.callnotes.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class Transcriber(private val provider: Provider, private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Returns transcript text, or throws on failure. */
    fun transcribe(audio: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", provider.transcribeModel)
            .addFormDataPart("response_format", "json")
            .addFormDataPart(
                "file", audio.name,
                audio.asRequestBody("audio/m4a".toMediaType())
            )
            .build()
        val req = Request.Builder()
            .url("${provider.baseUrl}/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Transcription failed (${resp.code}): $text")
            return JSONObject(text).optString("text")
        }
    }
}
