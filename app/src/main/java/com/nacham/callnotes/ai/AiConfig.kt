package com.nacham.callnotes.ai

/**
 * OpenAI-compatible endpoints. Groq is cheaper/faster and supports the same API shape.
 * The user supplies their own key in Settings; it never leaves the device except to this host.
 */
enum class Provider(
    val label: String,
    val baseUrl: String,
    val transcribeModel: String,
    val chatModel: String
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "whisper-1", "gpt-4o-mini"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "whisper-large-v3", "llama-3.3-70b-versatile");

    companion object {
        fun from(name: String?): Provider = entries.firstOrNull { it.name == name } ?: GROQ
    }
}
