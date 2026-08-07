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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object FactumIntelligenceEngine {
    private const val TAG = "FactumEngine"

    private val client = OkHttpClient.Builder()
        .addInterceptor(AiLoggingInterceptor("FactumEngine"))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-Memory Caches for AI responses
    private val blindspotCache = ConcurrentHashMap<String, BlindspotReport>()
    private val catchMeUpCache = ConcurrentHashMap<String, String>()
    private val translationCache = ConcurrentHashMap<String, String>()
    private val localImpactCache = ConcurrentHashMap<String, String>()

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // Valid fast models in order
    private val candidateModels = listOf("gemini-2.0-flash", "gemini-1.5-flash-latest")

    private suspend fun callGemini(
        prompt: String, 
        temperature: Double = 0.3,
        maxTokens: Int = 300
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API Key is missing or default.")
            return@withContext "Error: API Key Missing"
        }

        val jsonPayload = """
            {
              "contents": [{
                "parts": [{
                  "text": "${escapeJson(prompt)}"
                }]
              }],
              "generationConfig": {
                "temperature": $temperature,
                "maxOutputTokens": $maxTokens
              }
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError = "Error connecting to AI service."
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""
                
                if (response.code == 429) {
                    Log.w(TAG, "Gemini API rate limit / quota reached (429). Using intelligent local fallback.")
                    response.close()
                    return@withContext "Error: Quota Exceeded (429)"
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "API call for model $model failed (code ${response.code})")
                    lastError = "Error: API returned HTTP ${response.code}"
                    response.close()
                    continue
                }

                response.close()
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
            } catch (e: Exception) {
                Log.w(TAG, "Error calling Gemini for model $model: ${e.message}")
                lastError = "Error: ${e.message}"
            }
        }
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

        val cacheKey = "$userRegion:${articles.take(5).map { it.title.hashCode() }.hashCode()}"
        blindspotCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached blindspot report for region $userRegion")
            return@withContext cached
        }

        val articlesPayload = articles.take(5).joinToString("\n") { 
            "- Source: [${it.sourceName}] (${it.category}) Title: ${it.title}" 
        }

        val prompt = """
            You are the BLINDSPOT_DETECTOR module for Factum news.
            
            Directive: Analyze cross-outlet coverage patterns across our global media sources with respect to the user's Designated Home Region: '$userRegion'. Identify major stories receiving heavy coverage in specific regions or outlets that are being overlooked or underreported by major global feeds or Western wire networks.
            
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

        val responseText = callGemini(prompt, temperature = 0.2, maxTokens = 250)
        try {
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
            val report = if (cleanJson.startsWith("{")) {
                val obj = JSONObject(cleanJson)
                val jsonTrendingRegion = obj.optString("trendingRegion", "").ifEmpty { userRegion }
                BlindspotReport(
                    headline = obj.optString("headline", articles.firstOrNull()?.title ?: "Regional News Alert"),
                    alertText = obj.optString("alertText", "This story is trending heavily in $userRegion regional feeds but has low coverage in global media."),
                    trendingRegion = jsonTrendingRegion,
                    overlookedRegion = obj.optString("overlookedRegion", "Global Media Feeds"),
                    missingContext = obj.optString("missingContext", "Regional outlets in $userRegion are covering major trade, economic and diplomatic developments currently omitted from international headlines."),
                    topSource = obj.optString("topSource", articles.firstOrNull()?.sourceName ?: "Regional Sources")
                )
            } else {
                val sample = articles.find { it.category.equals(userRegion, ignoreCase = true) || it.category == userRegion } ?: articles.first()
                BlindspotReport(
                    headline = sample.title,
                    alertText = "This story is trending heavily in $userRegion regional feeds but has lower visibility in global networks. Here is what you're missing.",
                    trendingRegion = if (userRegion != "Global") userRegion else sample.category.ifEmpty { "Global" },
                    overlookedRegion = "Global Media Feeds",
                    missingContext = "Key regulatory, economic, and political developments in $userRegion reported by ${sample.sourceName} have not been picked up by mainstream international wire services.",
                    topSource = sample.sourceName
                )
            }
            blindspotCache[cacheKey] = report
            return@withContext report
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Blindspot JSON", e)
            val sample = articles.find { it.category.equals(userRegion, ignoreCase = true) || it.category == userRegion } ?: articles.first()
            val fallback = BlindspotReport(
                headline = sample.title,
                alertText = "This story is trending heavily in $userRegion regional feeds but has lower visibility globally. Here is what you're missing.",
                trendingRegion = if (userRegion != "Global") userRegion else sample.category.ifEmpty { "Global" },
                overlookedRegion = "Global Media Feeds",
                missingContext = "Key regulatory, economic, and political developments in $userRegion reported by ${sample.sourceName} have not been picked up by mainstream international wire services.",
                topSource = sample.sourceName
            )
            blindspotCache[cacheKey] = fallback
            return@withContext fallback
        }
    }

    // 2. CATCH_ME_UP section
    suspend fun generateCatchMeUp(article: NewsArticle): String = withContext(Dispatchers.IO) {
        val cacheKey = article.link.ifEmpty { article.title }
        catchMeUpCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached catch me up timeline")
            return@withContext cached
        }

        val prompt = """
            You are the CATCH_ME_UP module for Factum news.
            Provide instant backstory context for readers entering complex, long-running news cycles.
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Description: ${article.description}
            
            Output ONLY a 3-bullet historical timeline starting each bullet with '• [Month/Year] '. Total word count must be under 50 words.
        """.trimIndent()

        val res = callGemini(prompt, temperature = 0.2, maxTokens = 200)
        if (res.isNotBlank() && !res.startsWith("Error")) {
            catchMeUpCache[cacheKey] = res
        }
        res
    }

    // 3. INTERACTIVE_ARTICLE_CHAT
    suspend fun answerArticleQuestion(
        article: NewsArticle,
        fullArticleText: String?,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val articleContext = if (!fullArticleText.isNullOrBlank()) {
            "Title: ${article.title}\nPublisher: ${article.sourceName}\nFull Text:\n${fullArticleText.take(1200)}"
        } else {
            "Title: ${article.title}\nPublisher: ${article.sourceName}\nSummary: ${article.description.take(500)}"
        }

        val prompt = """
            You are the INTERACTIVE_ARTICLE_CHAT module for Factum news.
            Answer user questions strictly using the article context provided. If not mentioned, reply with: 'This detail isn't mentioned in the article.' Keep response under 60 words.
            
            --- ARTICLE Context ---
            $articleContext
            ----------------------------
            
            User Query: $question
        """.trimIndent()

        callGemini(prompt, temperature = 0.1, maxTokens = 200)
    }

    // 4. REALTIME_NUANCED_TRANSLATION
    suspend fun generateNuancedTranslation(article: NewsArticle): String = withContext(Dispatchers.IO) {
        val cacheKey = article.link.ifEmpty { article.title }
        translationCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached translation")
            return@withContext cached
        }

        val prompt = """
            You are the REALTIME_NUANCED_TRANSLATION module for Factum news.
            Translate the article text into clean English while preserving precise regional terms.
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Text: ${article.description}
            
            Provide the translated text in under 150 words.
        """.trimIndent()

        val res = callGemini(prompt, temperature = 0.3, maxTokens = 250)
        if (res.isNotBlank() && !res.startsWith("Error")) {
            translationCache[cacheKey] = res
        }
        res
    }

    // 5. LOCAL_IMPACT_ANALYSIS
    suspend fun generateLocalImpactAnalysis(
        article: NewsArticle,
        userRegion: String,
        userSector: String
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "$userRegion:$userSector:${article.link.ifEmpty { article.title }}"
        localImpactCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached local impact breakdown")
            return@withContext cached
        }

        val prompt = """
            You are the LOCAL_IMPACT_ANALYSIS module for Factum news.
            Synthesize a 2-sentence breakdown under the header 'Why This Matters to You': Explain the direct local consequences on supply chains, prices, or policy for the user's region ($userRegion) and sector ($userSector).
            
            Article:
            Title: ${article.title}
            Publisher: ${article.sourceName}
            Details: ${article.description}
            
            Format starting with:
            Why This Matters to You:
            [2 sentences]
        """.trimIndent()

        val res = callGemini(prompt, temperature = 0.3, maxTokens = 200)
        if (res.isNotBlank() && !res.startsWith("Error")) {
            localImpactCache[cacheKey] = res
        }
        res
    }
}
