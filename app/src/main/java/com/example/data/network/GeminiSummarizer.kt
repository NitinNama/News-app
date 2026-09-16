package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiSummarizer {
    private const val TAG = "GeminiSummarizer"
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(AiLoggingInterceptor("GeminiSummarizer"))
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private val candidateModels = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-flash-latest")

    suspend fun generateSummary(title: String, description: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not configured.")
            return@withContext "Please configure your GEMINI_API_KEY in the Secrets panel."
        }

        val prompt = "Provide a very short, highly accurate, single-paragraph summary (under 60 words) of the following article. Focus strictly on the key facts. Do not include introductory phrases like 'This article is about' or 'Summary:', just start directly with the content:\nTitle: $title\nDescription: $description"
        
        val jsonPayload = """
            {
              "contents": [{
                "parts": [{
                  "text": "${escapeJson(prompt)}"
                }]
              }],
              "generationConfig": {
                "temperature": 0.4
              }
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError = "Unable to generate summary."
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "API call for model $model failed with code: ${response.code}, body: $bodyString")
                        lastError = if (response.code == 429) {
                            "AI rate limit reached (5 requests/min). Please try again in a few seconds."
                        } else {
                            "Failed to generate summary (Error ${response.code})."
                        }
                        return@use
                    }

                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text", "No summary text generated.")
                            }
                        }
                    }
                    lastError = "Could not extract summary from response."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Gemini API for model $model", e)
                lastError = "Error: Unable to connect to AI engine."
            }
        }
        return@withContext lastError
    }

    suspend fun generateFullArticle(title: String, description: String, sourceName: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not configured.")
            return@withContext "Please configure your GEMINI_API_KEY in the Secrets panel."
        }

        val prompt = "You are a professional, objective investigative journalist writing for a premier world news publication. Based on the following brief article details:\n\nTitle: $title\nPublisher: $sourceName\nBrief: $description\n\nPlease reconstruct and write a full-length, detailed, engaging, and professional news article of approximately 300 to 450 words. Stick closely to the facts mentioned, expanding them with logical journalistic depth and professional analysis. Avoid personal commentary, flowery or sensational marketing adjectives, and start directly with the article. Do not include markdown headers (like # or ##) or intro text; use standard paragraphs and a clean news layout."
        
        val jsonPayload = """
            {
              "contents": [{
                "parts": [{
                  "text": "${escapeJson(prompt)}"
                }]
              }],
              "generationConfig": {
                "temperature": 0.5
              }
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError = "Unable to generate full article."
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "API call for model $model failed with code: ${response.code}, body: $bodyString")
                        lastError = if (response.code == 429) {
                            "AI rate limit reached (5 requests/min). Please try again in a few seconds."
                        } else {
                            "Failed to generate full article (Error ${response.code})."
                        }
                        return@use
                    }

                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text", "No article content generated.")
                            }
                        }
                    }
                    lastError = "Could not extract article content from response."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating full article for model $model", e)
                lastError = "Error: Unable to connect to AI engine."
            }
        }
        return@withContext lastError
    }
}
