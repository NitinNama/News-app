package com.example.data.network

import com.example.data.model.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class NewsNetworkClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 6
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val rssParser = RssParser()

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ ->
                    response.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun fetchFeed(url: String, sourceName: String, category: String): List<NewsArticle> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, application/atom+xml, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }
                val body = response.body
                if (body != null) {
                    val rawBytes = body.bytes()
                    if (rawBytes.isEmpty()) return@withContext emptyList()

                    // Quickly detect non-RSS HTML responses (like Cloudflare or 200 OK login walls)
                    val previewLength = minOf(rawBytes.size, 300)
                    val preview = String(rawBytes, 0, previewLength, Charsets.UTF_8).lowercase()
                    if (preview.contains("<!doctype html") || (preview.contains("<html") && !preview.contains("<rss") && !preview.contains("<feed"))) {
                        return@withContext emptyList()
                    }

                    var content = String(rawBytes, Charsets.UTF_8)
                    // Remove UTF-8 Byte Order Mark (BOM) if present
                    if (content.startsWith("\uFEFF")) {
                        content = content.substring(1)
                    }
                    // Strip any leading non-XML characters before '<'
                    val xmlStartIndex = content.indexOf('<')
                    if (xmlStartIndex > 0) {
                        content = content.substring(xmlStartIndex)
                    }

                    val cleanStream: InputStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                    return@withContext rssParser.parse(cleanStream, sourceName, category)
                }
            }
        } catch (e: Exception) {
            // Silently ignore individual feed failures so working feeds continue without disruption
        }
        return@withContext emptyList()
    }
}
