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
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
            var currentPubDate = ""
            var currentImageUrl: String? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("item", ignoreCase = true)) {
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
                                    currentLink = readText(parser)
                                }
                                name.equals("description", ignoreCase = true) -> {
                                    val desc = readText(parser)
                                    currentDescription = stripHtml(desc)
                                    // Try to extract image source from HTML description if no other image is found yet
                                    if (currentImageUrl == null) {
                                        currentImageUrl = extractImageFromHtml(desc)
                                    }
                                }
                                name.equals("pubDate", ignoreCase = true) -> {
                                    currentPubDate = formatPubDate(readText(parser))
                                }
                                name.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type")
                                    if (type != null && type.startsWith("image/", ignoreCase = true)) {
                                        currentImageUrl = parser.getAttributeValue(null, "url")
                                    }
                                }
                                name.equals("media:content", ignoreCase = true) || name.equals("content", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null) {
                                        currentImageUrl = url
                                    }
                                }
                                name.equals("media:thumbnail", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null) {
                                        currentImageUrl = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("item", ignoreCase = true)) {
                            insideItem = false
                            if (currentTitle.isNotEmpty() && currentLink.isNotEmpty()) {
                                articles.add(
                                    NewsArticle(
                                        title = currentTitle.trim(),
                                        link = currentLink.trim(),
                                        description = currentDescription.trim().ifEmpty { "No description available." },
                                        pubDate = currentPubDate.trim().ifEmpty { "Recently" },
                                        sourceName = sourceName,
                                        category = category,
                                        imageUrl = currentImageUrl
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
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun stripHtml(html: String): String {
        // Strip HTML tags using regex
        val tagPattern = Pattern.compile("<[^>]*>")
        val matcher = tagPattern.matcher(html)
        var clean = matcher.replaceAll("")
        // Decode common HTML entities
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
        // Look for <img src="url" ...> inside description
        try {
            val imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
            val matcher = imgPattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun formatPubDate(rawDate: String): String {
        // Clean up or format standard RSS pubDate (e.g. "Fri, 17 Jul 2026 22:30:00 GMT" -> "17 Jul 2026, 10:30 PM")
        // To be safe and fast, let's just return a simpler readable string if parsing fails,
        // or parse standard RFC-822 date formats.
        return try {
            val clean = rawDate.replace(Regex("\\s+"), " ").trim()
            if (clean.length > 16) {
                // Slice out the main part "Fri, 17 Jul 2026 22:30"
                // Let's just return a beautifully trimmed date string
                val parts = clean.split(" ")
                if (parts.size >= 4) {
                    val dayOfWeek = parts[0] // e.g. "Fri,"
                    val day = parts[1] // e.g. "17"
                    val month = parts[2] // e.g. "Jul"
                    val year = parts[3] // e.g. "2026"
                    var time = ""
                    if (parts.size >= 5) {
                        // Extract hour:minute
                        val timeParts = parts[4].split(":")
                        if (timeParts.size >= 2) {
                            val hour = timeParts[0].toIntOrNull() ?: 0
                            val minute = timeParts[1]
                            val ampm = if (hour >= 12) "PM" else "AM"
                            val formattedHour = if (hour % 12 == 0) 12 else hour % 12
                            time = ", $formattedHour:$minute $ampm"
                        }
                    }
                    "$day $month $year$time"
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
