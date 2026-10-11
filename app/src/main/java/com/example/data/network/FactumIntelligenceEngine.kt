package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.BlindspotReport
import com.example.data.model.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FactumIntelligenceEngine {
    private const val TAG = "FactumEngine"

    private val client = OkHttpClient.Builder()
        .addInterceptor(AiLoggingInterceptor("FactumEngine"))
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

    private val baseCandidateModels = listOf("gemini-3.1-flash-lite-preview", "gemini-flash-latest", "gemini-3.5-flash")
    private val modelCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun getAvailableCandidateModels(): List<String> {
        val now = System.currentTimeMillis()
        return baseCandidateModels.sortedBy { model ->
            val cooldownUntil = modelCooldowns[model] ?: 0L
            if (cooldownUntil > now) 1 else 0
        }
    }

    private suspend fun callGemini(prompt: String, temperature: Double = 0.3): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is missing or invalid.")
            return@withContext "API key missing. Please configure GEMINI_API_KEY in AI Studio Secrets."
        }

        val jsonPayload = """
            {
              "contents": [{
                "parts": [{
                  "text": "${escapeJson(prompt)}"
                }]
              }],
              "generationConfig": {
                "temperature": $temperature
              }
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError = "Error connecting to AI service."
        val modelsToTry = getAvailableCandidateModels()
        for (model in modelsToTry) {
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
                        if (response.code == 503 || response.code == 429) {
                            // Temporary high demand / rate limit: cool down this model for 60s
                            modelCooldowns[model] = System.currentTimeMillis() + 60_000L
                            Log.w(TAG, "Model $model is temporarily unavailable (${response.code}). Trying fallback model...")
                        } else {
                            Log.w(TAG, "API call for model $model returned HTTP ${response.code}. Trying fallback model...")
                        }
                        lastError = when (response.code) {
                            503 -> "AI model is currently experiencing temporary high demand. Please try again shortly."
                            429 -> "AI rate limit reached. Please try again in a few seconds."
                            else -> "Error: API returned HTTP ${response.code}"
                        }
                        return@use
                    }

                    // Success: remove from cooldown if present
                    modelCooldowns.remove(model)

                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text", "No response text generated.")
                            }
                        }
                    }
                    lastError = "Could not parse AI response."
                }
            } catch (e: Exception) {
                Log.w(TAG, "Transient network error calling Gemini for model $model: ${e.message}. Trying next candidate...")
                lastError = "Error connecting to AI service: ${e.message}"
            }
        }
        Log.e(TAG, "All candidate Gemini models failed. Last error: $lastError")
        return@withContext lastError
    }

    // 1. BLINDSPOT_DETECTOR
    suspend fun detectBlindspots(articles: List<NewsArticle>, userRegion: String = "Global"): BlindspotReport = withContext(Dispatchers.IO) {
        if (articles.isEmpty()) {
            return@withContext BlindspotReport(
                headline = "No active articles to scan",
                alertText = "This story is trending heavily in regional sources for $userRegion but has low coverage in global feeds. Here is what you're missing.",
                trendingRegion = userRegion,
                overlookedRegion = "Global Networks",
                missingContext = "Regional outlets are covering key localized geopolitical and supply chain developments.",
                topSource = "Global Wire Services"
            )
        }

        val articlesPayload = articles.take(8).joinToString("\n") { 
            "- Source: [${it.sourceName}] (${it.category}) Title: ${it.title}" 
        }

        val prompt = """
            You are the BLINDSPOT_DETECTOR module for Factum news.
            
            Directive: Analyze cross-outlet coverage patterns across our global media sources with respect to the user's Designated Home Region: '$userRegion'. Identify major stories receiving heavy coverage in specific regions or outlets (e.g., Deutsche Welle, France24, AFP, Premium Times, Mail & Guardian, The EastAfrican, Japan Times, ABC News Australia, PTI, The Hindu, China Daily) that are being overlooked or underreported by major global feeds or Western wire networks.
            
            Execution Command:
            "Scan incoming article clusters. Flag stories with high coverage volume in regional source groups but near-zero footprint in main global feeds. Display a 'Blindspot Alert' card to the user detailing what is being overlooked in relation to $userRegion."
            
            Incoming Articles:
            $articlesPayload
            
            Provide a strictly valid JSON response with these keys:
            {
              "headline": "Short headline of the blindspot story",
              "alertText": "This story is trending heavily in regional feeds but has low coverage in global media.",
              "trendingRegion": "Trending Region Name",
              "overlookedRegion": "Global Feeds",
              "missingContext": "2-sentence breakdown explaining the specific economic, geopolitical, or policy facts missing in global coverage.",
              "topSource": "Primary outlet reporting it"
            }
            Do not include markdown code block backticks surrounding JSON, just valid raw JSON text.
        """.trimIndent()

        val responseText = callGemini(prompt, temperature = 0.2)
        try {
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
            if (!cleanJson.startsWith("{")) {
                val sample = articles.find { it.category.equals(userRegion, ignoreCase = true) || it.category == userRegion } ?: articles.first()
                return@withContext BlindspotReport(
                    headline = sample.title,
                    alertText = "This story is trending heavily in $userRegion regional feeds but has lower visibility in global networks. Here is what you're missing.",
                    trendingRegion = if (userRegion != "Global") userRegion else sample.category.ifEmpty { "Global" },
                    overlookedRegion = "Global Media Feeds",
                    missingContext = "Key regulatory, economic, and political developments in $userRegion reported by ${sample.sourceName} have not been picked up by mainstream international wire services.",
                    topSource = sample.sourceName
                )
            }
            val obj = JSONObject(cleanJson)
            val jsonTrendingRegion = obj.optString("trendingRegion", "").ifEmpty { userRegion }
            return@withContext BlindspotReport(
                headline = obj.optString("headline", articles.firstOrNull()?.title ?: "Regional News Alert"),
                alertText = obj.optString("alertText", "This story is trending heavily in $userRegion regional feeds but has low coverage in global media."),
                trendingRegion = jsonTrendingRegion,
                overlookedRegion = obj.optString("overlookedRegion", "Global Media Feeds"),
                missingContext = obj.optString("missingContext", "Regional outlets in $userRegion are covering major trade, economic and diplomatic developments currently omitted from international headlines."),
                topSource = obj.optString("topSource", articles.firstOrNull()?.sourceName ?: "Regional Sources")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Blindspot JSON", e)
            val sample = articles.find { it.category.equals(userRegion, ignoreCase = true) || it.category == userRegion } ?: articles.first()
            return@withContext BlindspotReport(
                headline = sample.title,
                alertText = "This story is trending heavily in $userRegion regional feeds but has lower visibility globally. Here is what you're missing.",
                trendingRegion = if (userRegion != "Global") userRegion else sample.category.ifEmpty { "Global" },
                overlookedRegion = "Global Media Feeds",
                missingContext = "Key regulatory, economic, and political developments in $userRegion reported by ${sample.sourceName} have not been picked up by mainstream international wire services.",
                topSource = sample.sourceName
            )
        }
    }

    // 2. CATCH_ME_UP section
    suspend fun generateCatchMeUp(article: NewsArticle): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are the CATCH_ME_UP module for Factum news.
            
            Directive: Provide instant historical context for readers entering complex, long-running news cycles mid-story.
            
            Execution Command:
            "When triggered on an article, ignore the breaking news text and extract the backstory. Generate a 3-bullet historical timeline outlining the key events from the past 6 to 12 months that led to today’s headline. Keep it strictly factual, chronologically ordered, and under 60 words total."
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Description: ${article.description}
            
            Output ONLY the 3-bullet historical timeline starting each bullet with '• [Month/Year] '. Total word count must be under 60 words.
        """.trimIndent()

        callGemini(prompt, temperature = 0.2)
    }

    // 3. INTERACTIVE_ARTICLE_CHAT
    suspend fun answerArticleQuestion(
        article: NewsArticle,
        fullArticleText: String?,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val articleContext = if (!fullArticleText.isNullOrBlank()) {
            "Title: ${article.title}\nPublisher: ${article.sourceName}\nFull Text:\n$fullArticleText"
        } else {
            "Title: ${article.title}\nPublisher: ${article.sourceName}\nSummary/Description: ${article.description}"
        }

        val prompt = """
            You are the INTERACTIVE_ARTICLE_CHAT module for Factum news.
            
            Directive: Power an in-article conversational interface that answers reader questions using only the specific article text provided.
            
            Execution Command:
            "Treat the current article text as your sole source of truth. Answer user queries strictly using facts present in the text. If a user asks a question that the article does not explicitly answer, respond with: 'This detail isn't mentioned in the article.' Never pull outside assumptions or hallucinate facts beyond the payload."
            
            --- ARTICLE TEXT PAYLOAD ---
            $articleContext
            ----------------------------
            
            User Query: $question
        """.trimIndent()

        callGemini(prompt, temperature = 0.1)
    }

    // 4. REALTIME_NUANCED_TRANSLATION
    suspend fun generateNuancedTranslation(article: NewsArticle): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are the REALTIME_NUANCED_TRANSLATION module for Factum news.
            
            Directive: Translate primary source news into clean, accurate English while preserving local political, cultural, and industry terminology.
            
            Execution Command:
            "Translate foreign-language article feeds into natural English. Do not perform literal word-for-word translations if they distort meaning. Preserve precise regional terminology, economic metrics, and official titles. Add a subtle inline bracket [Note: ...] only when a regional idiom or policy name requires brief context."
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Text: ${article.description}
            
            Provide the translated and contextualized article text. Ensure any required context is provided via inline brackets like [Note: ...].
        """.trimIndent()

        callGemini(prompt, temperature = 0.3)
    }

    // 5. LOCAL_IMPACT_ANALYSIS
    suspend fun generateLocalImpactAnalysis(
        article: NewsArticle,
        userRegion: String,
        userSector: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are the LOCAL_IMPACT_ANALYSIS module for Factum news.
            
            Directive: Bridge macro-level global news down to the individual user’s daily life and regional context.
            
            Execution Command:
            "Read the global/national story payload and cross-reference it with the user's designated region or sector. Synthesize a 2-sentence breakdown under the header 'Why This Matters to You': Explain the direct local consequences, such as expected shifts in supply chains, local fuel/commodity prices, or domestic policy impact."
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Category: ${article.category}
            Details: ${article.description}
            
            User Profile:
            - Designated Region: $userRegion
            - Designated Sector: $userSector
            
            Format your response starting exactly with the header:
            Why This Matters to You:
            [2-sentence breakdown explaining direct local consequences on supply chains, fuel/commodity prices, or domestic policy impact].
        """.trimIndent()

        callGemini(prompt, temperature = 0.3)
    }
}
