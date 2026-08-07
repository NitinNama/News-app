package com.example.data.repository

import com.example.data.local.NewsDao
import com.example.data.model.NewsArticle
import com.example.data.model.NewsSource
import com.example.data.model.SavedArticle
import com.example.data.model.CachedArticle
import com.example.data.network.NewsNetworkClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NewsRepository(
    private val newsDao: NewsDao,
    private val networkClient: NewsNetworkClient = NewsNetworkClient()
) {
    val allSources: Flow<List<NewsSource>> = newsDao.getAllSources()
    val savedArticles: Flow<List<SavedArticle>> = newsDao.getSavedArticles()

    // Prepopulate default news sources if empty or missing
    suspend fun initializeDefaultSources() {
        val defaults = listOf(
            NewsSource(name = "BBC World News", url = "https://feeds.bbci.co.uk/news/rss.xml", category = "World"),
            NewsSource(name = "CNN World News", url = "http://rss.cnn.com/rss/cnn_world.rss", category = "World"),
            NewsSource(name = "The Guardian World", url = "https://www.theguardian.com/world/rss", category = "World"),
            NewsSource(name = "Al Jazeera News", url = "https://www.aljazeera.com/xml/rss/all.xml", category = "Middle East"),
            NewsSource(name = "DW World News (Deutsche Welle)", url = "https://rss.dw.com/xml/rss-en-world", category = "Europe"),
            NewsSource(name = "Agence France-Presse (AFP)", url = "https://www.france24.com/en/rss", category = "World"),
            NewsSource(name = "Associated Press (AP)", url = "https://news.google.com/rss/search?q=when:24h+source:Associated+Press&hl=en-US&gl=US&ceid=US:en", category = "World"),
            NewsSource(name = "Reuters", url = "https://news.google.com/rss/search?q=when:24h+source:Reuters&hl=en-US&gl=US&ceid=US:en", category = "World"),
            NewsSource(name = "The Wall Street Journal - News Desk", url = "https://feeds.a.dj.com/rss/RSSWorldNews.xml", category = "World"),
            NewsSource(name = "PBS NewsHour", url = "https://www.pbs.org/newshour/feeds/rss/headlines", category = "World"),
            NewsSource(name = "The Globe and Mail", url = "https://www.theglobeandmail.com/arc/outboundfeeds/rss/category/canada/", category = "World"),
            NewsSource(name = "Premium Times", url = "https://www.premiumtimesng.com/feed", category = "World"),
            NewsSource(name = "Mail & Guardian", url = "https://mg.co.za/feed/", category = "World"),
            NewsSource(name = "The EastAfrican", url = "https://www.theeastafrican.co.ke/service/rss/tea/434658/feed.rss", category = "World"),
            NewsSource(name = "The Japan Times", url = "https://www.japantimes.co.jp/feed/", category = "Asia"),
            NewsSource(name = "ABC News (Australia)", url = "https://www.abc.net.au/news/feed/51120/rss.xml", category = "Asia"),
            
            // Russian & Eastern Europe
            NewsSource(name = "The Moscow Times", url = "https://www.themoscowtimes.com/rss/news", category = "Russia"),
            NewsSource(name = "Novaya Gazeta Europe", url = "https://novayagazeta.eu/feed/rss/all", category = "Europe"),
            NewsSource(name = "Meduza", url = "https://meduza.io/rss/en/all", category = "Europe"),
            NewsSource(name = "Kommersant", url = "https://www.kommersant.ru/RSS/main.xml", category = "Business"),
            NewsSource(name = "RBK News", url = "https://rssexport.rbc.ru/rbcnews/news/30/full.rss", category = "Business"),

            // Southeast Asia
            NewsSource(name = "CNA (Channel NewsAsia)", url = "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml", category = "Asia"),
            NewsSource(name = "The Straits Times", url = "https://www.straitstimes.com/news/rss", category = "Asia"),
            NewsSource(name = "The Jakarta Post", url = "https://www.thejakartapost.com/rss/news", category = "Asia"),
            NewsSource(name = "Rappler", url = "https://www.rappler.com/feed", category = "Asia"),
            NewsSource(name = "Bangkok Post", url = "https://www.bangkokpost.com/rss/data/most-recent.xml", category = "Asia"),
            NewsSource(name = "Malaysiakini", url = "https://www.malaysiakini.com/rss/en/news.rss", category = "Asia"),

            // Middle East
            NewsSource(name = "Arab News", url = "https://www.arabnews.com/rss.xml", category = "Middle East"),
            NewsSource(name = "Haaretz", url = "https://www.haaretz.com/misc/rss", category = "Middle East"),
            NewsSource(name = "L'Orient Today", url = "https://www.lorientlejour.com/rss", category = "Middle East"),
            NewsSource(name = "The National (UAE)", url = "https://www.thenationalnews.com/rss", category = "Middle East"),

            // Latin America
            NewsSource(name = "Armando.info", url = "https://armando.info/feed/", category = "Latin America"),
            NewsSource(name = "El Mercurio", url = "https://www.emol.com/rss/rss.asp", category = "Latin America"),
            NewsSource(name = "La Nación (Argentina)", url = "https://www.lanacion.com.ar/arc/outboundfeeds/rss/", category = "Latin America"),
            NewsSource(name = "Clarín (Argentina)", url = "https://www.clarin.com/rss/lo-ultimo/", category = "Latin America"),
            NewsSource(name = "El Espectador", url = "https://www.elespectador.com/arc/outboundfeeds/rss/", category = "Latin America"),
            NewsSource(name = "El Tiempo", url = "https://www.eltiempo.com/rss/colombia.xml", category = "Latin America"),
            NewsSource(name = "Folha de S.Paulo", url = "https://feeds.folha.uol.com.br/emcimadahora/rss091.xml", category = "Latin America"),
            NewsSource(name = "O Globo", url = "https://oglobo.globo.com/rss.xml", category = "Latin America"),

            // India & China
            NewsSource(name = "Press Trust of India (PTI)", url = "https://news.google.com/rss/search?q=when:24h+source:%22Press+Trust+of+India%22&hl=en-IN&gl=IN&ceid=IN:en", category = "India"),
            NewsSource(name = "NDTV Top Stories", url = "https://feeds.feedburner.com/ndtvnews-top-stories", category = "India"),
            NewsSource(name = "The Times of India", url = "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms", category = "India"),
            NewsSource(name = "The Economic Times", url = "https://economictimes.indiatimes.com/rssfeedsdefault.cms", category = "India"),
            NewsSource(name = "Firstpost News", url = "https://www.firstpost.com/rss/india.xml", category = "India"),
            NewsSource(name = "Moneycontrol News", url = "https://www.moneycontrol.com/rss/latestnews.xml", category = "Finance"),
            NewsSource(name = "The Hindu", url = "https://www.thehindu.com/news/feeder/default.rss", category = "India"),
            NewsSource(name = "China Daily", url = "https://www.chinadaily.com.cn/rss/china_rss.xml", category = "China"),

            // Tech, Science, Business, Sports & Finance
            NewsSource(name = "Financial Times", url = "https://www.ft.com/rss/home/uk", category = "Finance"),
            NewsSource(name = "Yahoo Finance", url = "https://finance.yahoo.com/news/rssindex", category = "Finance"),
            NewsSource(name = "CoinDesk Crypto", url = "https://www.coindesk.com/arc/outboundfeeds/rss/", category = "Crypto"),
            NewsSource(name = "MarketWatch Stock Stories", url = "https://www.marketwatch.com/rss/topstories", category = "Stock Market"),
            NewsSource(name = "Investing.com Forex News", url = "https://www.investing.com/rss/news_1.rss", category = "Forex Market"),
            NewsSource(name = "CNBC Market Business", url = "https://www.cnbc.com/id/100003114/device/rss/rss.html", category = "Business"),
            NewsSource(name = "WSJ US Business", url = "https://feeds.a.dj.com/rss/WSJcomUSBusiness.xml", category = "Business"),
            NewsSource(name = "TechCrunch", url = "https://techcrunch.com/feed/", category = "Technology"),
            NewsSource(name = "NASA Breaking Science", url = "https://www.nasa.gov/news-release/feed/", category = "Science"),
            NewsSource(name = "Sky Sports", url = "https://www.skysports.com/rss/12040", category = "Sports")
        )

        val failingUrls = setOf(
            "https://www.channelnewsasia.com/api/v1/rss-out/news-feed/3391/rss.xml",
            "https://www.scmp.com/rss/911/feed.xml",
            "https://www.espn.com/espn/rss/news",
            "https://www.dailyfx.com/feeds/forex-market-news",
            "https://indianexpress.com/feed/",
            "https://www.ptinews.com/rss/news.xml",
            "https://www.caixinglobal.com/rss/news.xml",
            "https://www.xinhuanet.com/english/rss/englishrss.xml",
            "https://www.cgtn.com/rss/news.xml"
        )

        val existingSources = newsDao.getAllSources().first()
        
        // Remove old dead feeds
        for (source in existingSources) {
            if (failingUrls.contains(source.url)) {
                newsDao.deleteSource(source)
            }
        }

        // Add curated working feeds
        val updatedExistingSources = newsDao.getAllSources().first()
        val existingUrls = updatedExistingSources.map { it.url }.toSet()
        for (source in defaults) {
            if (!existingUrls.contains(source.url)) {
                newsDao.insertSource(source)
            } else {
                val existing = updatedExistingSources.find { it.url == source.url }
                if (existing != null && existing.name != source.name) {
                    newsDao.updateSource(existing.copy(name = source.name))
                }
            }
        }
    }

    // Get local cached articles from Room database for offline reading
    suspend fun getCachedNews(): List<NewsArticle> {
        val savedLinks = try {
            newsDao.getSavedArticles().first().map { it.link }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val cached = newsDao.getAllCachedArticles()
        return cached.map { cachedArticle ->
            NewsArticle(
                title = cachedArticle.title,
                link = cachedArticle.link,
                description = cachedArticle.description,
                pubDate = cachedArticle.pubDate,
                sourceName = cachedArticle.sourceName,
                category = cachedArticle.category,
                imageUrl = cachedArticle.imageUrl,
                isBookmarked = savedLinks.contains(cachedArticle.link)
            )
        }
    }

    // Fetch active feed articles from all enabled sources in parallel, with local SQLite offline caching fallback
    suspend fun fetchLiveNews(): List<NewsArticle> = coroutineScope {
        val enabledSources = newsDao.getEnabledSources().first()
        val deferreds = enabledSources.map { source ->
            async {
                try {
                    networkClient.fetchFeed(source.url, source.name, source.category)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        val allArticles = deferreds.awaitAll().flatten()
        val savedLinks = newsDao.getSavedArticles().first().map { it.link }.toSet()
        
        if (allArticles.isNotEmpty()) {
            // Update local SQLite cache
            try {
                newsDao.clearCachedArticles()
                val cachedBatch = allArticles.distinctBy { it.link }.map { article ->
                    CachedArticle(
                        link = article.link,
                        title = article.title,
                        description = article.description,
                        pubDate = article.pubDate,
                        sourceName = article.sourceName,
                        category = article.category,
                        imageUrl = article.imageUrl
                    )
                }
                newsDao.insertCachedArticles(cachedBatch)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            allArticles
                .distinctBy { it.link }
                .map { article ->
                    article.copy(isBookmarked = savedLinks.contains(article.link))
                }
        } else {
            // Fallback to local offline cache
            val cached = newsDao.getAllCachedArticles()
            cached.map { cachedArticle ->
                NewsArticle(
                    title = cachedArticle.title,
                    link = cachedArticle.link,
                    description = cachedArticle.description,
                    pubDate = cachedArticle.pubDate,
                    sourceName = cachedArticle.sourceName,
                    category = cachedArticle.category,
                    imageUrl = cachedArticle.imageUrl,
                    isBookmarked = savedLinks.contains(cachedArticle.link)
                )
            }
        }
    }

    // Source Management
    suspend fun addSource(name: String, url: String, category: String) {
        newsDao.insertSource(
            NewsSource(
                name = name,
                url = url,
                category = category,
                isEnabled = true,
                isCustom = true
            )
        )
    }

    suspend fun toggleSource(source: NewsSource) {
        newsDao.updateSource(source.copy(isEnabled = !source.isEnabled))
    }

    suspend fun deleteCustomSource(source: NewsSource) {
        if (source.isCustom) {
            newsDao.deleteSource(source)
        }
    }

    // Bookmark Management
    suspend fun toggleBookmark(article: NewsArticle) {
        val isSaved = newsDao.isArticleSaved(article.link) > 0
        if (isSaved) {
            newsDao.deleteSavedArticleByLink(article.link)
        } else {
            newsDao.insertSavedArticle(
                SavedArticle(
                    link = article.link,
                    title = article.title,
                    description = article.description,
                    pubDate = article.pubDate,
                    sourceName = article.sourceName,
                    category = article.category,
                    imageUrl = article.imageUrl
                )
            )
        }
    }

    suspend fun removeBookmark(link: String) {
        newsDao.deleteSavedArticleByLink(link)
    }

    // Portfolio tracking
    val allPortfolioItems: Flow<List<com.example.data.model.PortfolioItem>> = newsDao.getAllPortfolioItems()

    suspend fun insertPortfolioItem(item: com.example.data.model.PortfolioItem) {
        newsDao.insertPortfolioItem(item)
    }

    suspend fun deletePortfolioItem(id: Int) {
        newsDao.deletePortfolioItem(id)
    }
}
