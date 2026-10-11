package com.example.data.network

import android.util.Xml
import com.example.data.model.NewsArticle
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.regex.Pattern

class RssParser {
    fun parse(inputStream: InputStream, sourceName: String, category: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
            var currentPubDate = ""
            var currentImageUrl: String? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            insideItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentDescription = ""
                            currentPubDate = ""
                            currentImageUrl = null
                        } else if (insideItem) {
                            when {
                                name.equals("title", ignoreCase = true) -> {
                                    currentTitle = readText(parser)
                                }
                                name.equals("link", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val linkText = if (!parser.isEmptyElementTag) readText(parser).trim() else ""
                                    if (!href.isNullOrBlank()) {
                                        val rel = parser.getAttributeValue(null, "rel")
                                        if (rel == null || rel.equals("alternate", ignoreCase = true) || currentLink.isEmpty()) {
                                            currentLink = href
                                        }
                                    } else if (linkText.isNotBlank()) {
                                        currentLink = linkText
                                    }
                                }
                                name.equals("description", ignoreCase = true) ||
                                name.equals("summary", ignoreCase = true) ||
                                name.equals("content", ignoreCase = true) ||
                                name.equals("content:encoded", ignoreCase = true) -> {
                                    val descRaw = readText(parser)
                                    val descClean = stripHtml(descRaw)
                                    if (currentDescription.isEmpty() || descClean.length > currentDescription.length) {
                                        currentDescription = descClean
                                    }
                                    if (currentImageUrl == null) {
                                        currentImageUrl = extractImageFromHtml(descRaw)
                                    }
                                }
                                name.equals("pubDate", ignoreCase = true) ||
                                name.equals("published", ignoreCase = true) ||
                                name.equals("updated", ignoreCase = true) ||
                                name.equals("dc:date", ignoreCase = true) -> {
                                    currentPubDate = formatPubDate(readText(parser))
                                }
                                name.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type")
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null && (type == null || type.startsWith("image", ignoreCase = true))) {
                                        cleanImageUrl(url)?.let { currentImageUrl = it }
                                    }
                                }
                                name.equals("media:content", ignoreCase = true) ||
                                name.equals("media:thumbnail", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    cleanImageUrl(url)?.let { currentImageUrl = it }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            insideItem = false
                            if (currentTitle.isNotBlank() && currentLink.isNotBlank()) {
                                articles.add(
                                    NewsArticle(
                                        title = currentTitle.trim(),
                                        link = currentLink.trim(),
                                        description = currentDescription.trim().ifEmpty { "No description available." },
                                        pubDate = currentPubDate.trim().ifEmpty { "Recently" },
                                        sourceName = sourceName,
                                        category = category,
                                        imageUrl = cleanImageUrl(currentImageUrl)
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return articles
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        try {
            while (depth > 0) {
                when (val event = parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    private fun stripHtml(html: String): String {
        val tagPattern = Pattern.compile("<[^>]*>")
        val matcher = tagPattern.matcher(html)
        var clean = matcher.replaceAll("")
        clean = clean
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#160;", " ")
            .replace("&nbsp;", " ")
        return clean
    }

    private fun extractImageFromHtml(html: String): String? {
        try {
            val imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
            val matcher = imgPattern.matcher(html)
            while (matcher.find()) {
                val candidate = cleanImageUrl(matcher.group(1))
                if (candidate != null) {
                    return candidate
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        fun cleanImageUrl(rawUrl: String?): String? {
            if (rawUrl.isNullOrBlank()) return null
            var url = rawUrl.trim()

            // Discard base64 data URIs (usually tracking pixels or corrupt payloads that crash ImageDecoder)
            if (url.startsWith("data:", ignoreCase = true)) return null

            // Protocol-relative URLs (e.g. //lh3.googleusercontent.com/...)
            if (url.startsWith("//")) {
                url = "https:$url"
            }

            // Reject relative paths or unsupported non-http schemes
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                return null
            }

            // Clean common HTML entities that break URLs
            url = url.replace("&amp;", "&")
                .replace("&quot;", "")
                .replace("&#39;", "'")
                .trim()

            // Filter out tracking beacons, 1x1 pixels, and analytics trackers
            val lower = url.lowercase()
            if (lower.contains("pixel.gif") ||
                lower.contains("1x1") ||
                lower.contains("feedburner.com/~r/") ||
                lower.contains("doubleclick.net") ||
                lower.contains("stats.wordpress.com") ||
                lower.contains("tracking") ||
                lower.contains("beacon") ||
                lower.endsWith(".ico")
            ) {
                return null
            }

            return url
        }
    }

    private fun formatPubDate(rawDate: String): String {
        return try {
            val clean = rawDate.replace(Regex("\\s+"), " ").trim()
            if (clean.length > 16) {
                val parts = clean.split(" ")
                if (parts.size >= 4) {
                    val day = parts.getOrNull(1) ?: ""
                    val month = parts.getOrNull(2) ?: ""
                    val year = parts.getOrNull(3) ?: ""
                    var time = ""
                    if (parts.size >= 5) {
                        val timeParts = parts[4].split(":")
                        if (timeParts.size >= 2) {
                            val hour = timeParts[0].toIntOrNull() ?: 0
                            val minute = timeParts[1]
                            val ampm = if (hour >= 12) "PM" else "AM"
                            val formattedHour = if (hour % 12 == 0) 12 else hour % 12
                            time = ", $formattedHour:$minute $ampm"
                        }
                    }
                    if (day.isNotEmpty() && month.isNotEmpty() && year.isNotEmpty()) {
                        "$day $month $year$time"
                    } else {
                        clean
                    }
                } else {
                    clean
                }
            } else {
                clean
            }
        } catch (e: Exception) {
            rawDate
        }
    }
}
