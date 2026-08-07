package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.RssParser
import com.example.data.network.NewsNetworkClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Factum", appName)
  }

  @Test
  fun `test standard RSS parsing`() {
    val xml = """
        <rss version="2.0">
            <channel>
                <title>Mock Channel</title>
                <link>https://example.com</link>
                <description>Mock Description</description>
                <item>
                    <title>Mock Article Title</title>
                    <link>https://example.com/article1</link>
                    <description>This is a short description of the mock article.</description>
                    <pubDate>Fri, 17 Jul 2026 22:30:00 GMT</pubDate>
                </item>
            </channel>
        </rss>
    """.trimIndent()

    val parser = RssParser()
    val stream = ByteArrayInputStream(xml.toByteArray())
    val articles = parser.parse(stream, "Mock Source", "Mock Category")

    assertEquals(1, articles.size)
    val article = articles[0]
    assertEquals("Mock Article Title", article.title)
    assertEquals("https://example.com/article1", article.link)
    assertEquals("This is a short description of the mock article.", article.description)
    assertEquals("17 Jul 2026, 10:30 PM", article.pubDate)
    assertEquals("Mock Source", article.sourceName)
    assertEquals("Mock Category", article.category)
  }

  @Test
  fun `test RSS parsing with CDATA`() {
    val xml = """
        <rss version="2.0">
            <channel>
                <item>
                    <title><![CDATA[Title with CDATA & Special Characters < >]]></title>
                    <link><![CDATA[https://example.com/article_cdata]]></link>
                    <description><![CDATA[Description with <b>HTML</b> &amp; other tags <img src="https://example.com/image.jpg"/>]]></description>
                    <pubDate>Sun, 19 Jul 2026 05:00:00 GMT</pubDate>
                </item>
            </channel>
        </rss>
    """.trimIndent()

    val parser = RssParser()
    val stream = ByteArrayInputStream(xml.toByteArray())
    val articles = parser.parse(stream, "Mock Source", "Mock Category")

    assertEquals(1, articles.size)
    val article = articles[0]
    assertEquals("Title with CDATA & Special Characters < >", article.title)
    assertEquals("https://example.com/article_cdata", article.link)
    // Strip HTML should strip the <b> and <img> tags and clean entities
    assertEquals("Description with HTML & other tags", article.description)
    assertEquals("19 Jul 2026, 5:00 AM", article.pubDate)
    assertEquals("https://example.com/image.jpg", article.imageUrl)
  }

  @Test
  fun `test live RSS sources fetching`() = runBlocking {
    val client = NewsNetworkClient()
    val feedsToTest = listOf(
        Triple("BBC World News", "https://feeds.bbci.co.uk/news/rss.xml", "World"),
        Triple("CNA Asia News", "https://www.channelnewsasia.com/api/v1/rss-out/news-feed/3391/rss.xml", "Asia"),
        Triple("DW World News", "https://rss.dw.com/xml/rss-en-world", "Europe"),
        Triple("NDTV Top Stories", "https://feeds.feedburner.com/ndtvnews-top-stories", "India"),
        Triple("South China Morning Post", "https://www.scmp.com/rss/911/feed.xml", "China"),
        Triple("TechCrunch", "https://techcrunch.com/feed/", "Technology"),
        Triple("NASA Breaking Science", "https://www.nasa.gov/news-release/feed/", "Science"),
        Triple("CNBC Market Business", "https://www.cnbc.com/id/100003114/device/rss/rss.html", "Business"),
        Triple("ESPN General Sports", "https://www.espn.com/espn/rss/news", "Sports"),
        Triple("MarketWatch Stock Stories", "https://www.marketwatch.com/rss/topstories", "Stock Market"),
        Triple("Investing.com Forex News", "https://www.investing.com/rss/news_1.rss", "Forex Market"),
        Triple("DailyFX Forex News", "https://www.dailyfx.com/feeds/forex-market-news", "Forex Market"),
        Triple("WSJ US Business", "https://feeds.a.dj.com/rss/WSJcomUSBusiness.xml", "Business"),
        Triple("The Economic Times", "https://economictimes.indiatimes.com/rssfeedsdefault.cms", "India"),
        Triple("The Hindu", "https://www.thehindu.com/news/feeder/default.rss", "India"),
        Triple("The Indian Express", "https://indianexpress.com/feed/", "India"),
        Triple("Press Trust of India", "https://www.ptinews.com/rss/news.xml", "India"),
        Triple("Caixin Global", "https://www.caixinglobal.com/rss/news.xml", "China"),
        Triple("Xinhua News Agency", "https://www.xinhuanet.com/english/rss/englishrss.xml", "China"),
        Triple("CGTN", "https://www.cgtn.com/rss/news.xml", "China")
    )

    val results = mutableListOf<String>()
    var successfulFetches = 0

    println("======================================================================")
    println("                  Live RSS Sources Diagnostic Report                   ")
    println("======================================================================")

    for ((name, url, category) in feedsToTest) {
        try {
            val articles = client.fetchFeed(url, name, category)
            if (articles.isNotEmpty()) {
                successfulFetches++
                results.add(String.format("%-30s | WORKING  | Articles: %3d | Category: %s", name, articles.size, category))
            } else {
                results.add(String.format("%-30s | EMPTY    | Articles:   0 | Category: %s", name, category))
            }
        } catch (e: Exception) {
            results.add(String.format("%-30s | FAILED   | Error: %-15s | Category: %s", name, e.message ?: e.javaClass.simpleName, category))
        }
    }

    println("\nSummary of RSS Feeds Diagnostics:")
    for (res in results) {
        println(res)
    }
    println("======================================================================")
    println(String.format("Total: %d feeds | Working: %d | Failed/Empty: %d", feedsToTest.size, successfulFetches, feedsToTest.size - successfulFetches))
    println("======================================================================")

    assertTrue("At least some feeds should be fetched successfully", successfulFetches > 0)
  }
}

