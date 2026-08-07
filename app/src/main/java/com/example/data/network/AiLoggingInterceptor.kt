package com.example.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

class AiLoggingInterceptor(
    private val tag: String = "AiNetworkLogger"
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // Mask API Key in log URL to prevent credential exposure in logcat
        val urlString = request.url.toString().replace(Regex("key=[^&]+"), "key=REDACTED_KEY")

        val requestBodyString = try {
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            buffer.readString(Charsets.UTF_8)
        } catch (e: Exception) {
            "Could not read request body: ${e.message}"
        }

        Log.d(tag, "--> AI REQUEST [${request.method}] $urlString")
        if (requestBodyString.isNotBlank()) {
            Log.d(tag, "AI Request Payload: $requestBodyString")
        }

        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(tag, "<-- AI REQUEST FAILED (${System.currentTimeMillis() - startTime}ms): ${e.message}", e)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val responseBody = response.body
        val responseBodyString = responseBody?.string() ?: ""

        Log.d(tag, "<-- AI RESPONSE ${response.code} ${response.message} (${duration}ms)")
        if (responseBodyString.isNotBlank()) {
            Log.d(tag, "AI Response Payload: $responseBodyString")
        }

        // Re-create response with a fresh body since string() consumed the original stream
        val mediaType = responseBody?.contentType()
        val newResponseBody = responseBodyString.toResponseBody(mediaType)
        return response.newBuilder().body(newResponseBody).build()
    }
}
