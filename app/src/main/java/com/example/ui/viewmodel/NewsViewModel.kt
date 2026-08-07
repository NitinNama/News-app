package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NewsDatabase
import com.example.data.model.NewsArticle
import com.example.data.model.NewsSource
import com.example.data.model.SavedArticle
import com.example.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FeedState {
    object Loading : FeedState
    data class Success(val articles: List<NewsArticle>) : FeedState
    data class Error(val message: String) : FeedState
}

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NewsRepository
    private val prefs = application.getSharedPreferences("pulse_settings", android.content.Context.MODE_PRIVATE)

    private val _feedState = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private val _textSize = MutableStateFlow("Medium")
    val textSize: StateFlow<String> = _textSize.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _selectedVoiceTone = MutableStateFlow("Warm")
    val selectedVoiceTone: StateFlow<String> = _selectedVoiceTone.asStateFlow()

    private val _appIconVariation = MutableStateFlow("Default")
    val appIconVariation: StateFlow<String> = _appIconVariation.asStateFlow()

    private val _articleSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val articleSummaries: StateFlow<Map<String, String>> = _articleSummaries.asStateFlow()

    private val _summarizingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val summarizingStates: StateFlow<Map<String, Boolean>> = _summarizingStates.asStateFlow()

    private val _fullArticles = MutableStateFlow<Map<String, String>>(emptyMap())
    val fullArticles: StateFlow<Map<String, String>> = _fullArticles.asStateFlow()

    private val _generatingFullArticleStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val generatingFullArticleStates: StateFlow<Map<String, Boolean>> = _generatingFullArticleStates.asStateFlow()

    // --- Factum Intelligence Modules State ---
    private val _blindspotReport = MutableStateFlow<com.example.data.model.BlindspotReport?>(null)
    val blindspotReport: StateFlow<com.example.data.model.BlindspotReport?> = _blindspotReport.asStateFlow()

    private val _isBlindspotLoading = MutableStateFlow(false)
    val isBlindspotLoading: StateFlow<Boolean> = _isBlindspotLoading.asStateFlow()

    private val _catchMeUpTimelines = MutableStateFlow<Map<String, String>>(emptyMap())
    val catchMeUpTimelines: StateFlow<Map<String, String>> = _catchMeUpTimelines.asStateFlow()

    private val _catchMeUpLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val catchMeUpLoading: StateFlow<Map<String, Boolean>> = _catchMeUpLoading.asStateFlow()

    private val _articleChats = MutableStateFlow<Map<String, List<com.example.data.model.ArticleChatMessage>>>(emptyMap())
    val articleChats: StateFlow<Map<String, List<com.example.data.model.ArticleChatMessage>>> = _articleChats.asStateFlow()

    private val _chatLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val chatLoading: StateFlow<Map<String, Boolean>> = _chatLoading.asStateFlow()

    private val _nuancedTranslations = MutableStateFlow<Map<String, String>>(emptyMap())
    val nuancedTranslations: StateFlow<Map<String, String>> = _nuancedTranslations.asStateFlow()

    private val _translationLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val translationLoading: StateFlow<Map<String, Boolean>> = _translationLoading.asStateFlow()

    private val _userRegion = MutableStateFlow("Global")
    val userRegion: StateFlow<String> = _userRegion.asStateFlow()

    private val _userSector = MutableStateFlow("Finance & Markets")
    val userSector: StateFlow<String> = _userSector.asStateFlow()

    private val _localImpacts = MutableStateFlow<Map<String, String>>(emptyMap())
    val localImpacts: StateFlow<Map<String, String>> = _localImpacts.asStateFlow()

    private val _localImpactLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val localImpactLoading: StateFlow<Map<String, Boolean>> = _localImpactLoading.asStateFlow()

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()


    // Exposed Flows from database
    val allSources: StateFlow<List<NewsSource>>
    val savedArticles: StateFlow<List<SavedArticle>>
    val allPortfolioItems: StateFlow<List<com.example.data.model.PortfolioItem>>
    val marketQuotes: StateFlow<Map<String, com.example.data.network.MarketQuote>> = 
        com.example.data.network.MockFinancialService.quotesFlow

    init {
        // Load settings from SharedPreferences on startup
        val themePref = prefs.getString("theme_mode", "system")
        _isDarkMode.value = when (themePref) {
            "light" -> false
            "dark" -> true
            else -> null
        }
        _textSize.value = prefs.getString("text_size", "Medium") ?: "Medium"
        _ttsSpeed.value = prefs.getFloat("tts_speed", 1.0f)
        _ttsPitch.value = prefs.getFloat("tts_pitch", 1.0f)
        _selectedVoiceTone.value = prefs.getString("voice_tone", "Warm") ?: "Warm"
        _userRegion.value = prefs.getString("user_region", "Global") ?: "Global"
        _userSector.value = prefs.getString("user_sector", "Finance & Markets") ?: "Finance & Markets"


        val database = NewsDatabase.getDatabase(application)
        repository = NewsRepository(database.newsDao())

        viewModelScope.launch {
            while (true) {
                com.example.data.network.MockFinancialService.simulateTick()
                kotlinx.coroutines.delay(3000)
            }
        }

        // Detect current active app icon alias on startup
        try {
            val pm = application.packageManager
            val defaultAlias = "com.example.MainActivityDefault"
            val gold3DAlias = "com.example.MainActivityGold3D"
            val goldOutlineAlias = "com.example.MainActivityGoldOutline"
            val marbleAlias = "com.example.MainActivityMarble"
            
            val isDefaultEnabled = pm.getComponentEnabledSetting(
                android.content.ComponentName(application, defaultAlias)
            ) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            
            val isGold3DEnabled = pm.getComponentEnabledSetting(
                android.content.ComponentName(application, gold3DAlias)
            ) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            
            val isGoldOutlineEnabled = pm.getComponentEnabledSetting(
                android.content.ComponentName(application, goldOutlineAlias)
            ) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED

            val isMarbleEnabled = pm.getComponentEnabledSetting(
                android.content.ComponentName(application, marbleAlias)
            ) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            
            _appIconVariation.value = when {
                isGold3DEnabled -> "Gold3D"
                isGoldOutlineEnabled -> "GoldOutline"
                isMarbleEnabled -> "Marble"
                else -> "Default"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allSources = repository.allSources.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        savedArticles = repository.savedArticles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allPortfolioItems = repository.allPortfolioItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            repository.initializeDefaultSources()
            refreshNews()
            // Initialize FCM Token and sync region topics for breaking news push notifications
            com.example.util.NotificationHelper.fetchFcmToken(getApplication()) { token ->
                _fcmToken.value = token
            }
            com.example.util.NotificationHelper.syncFcmRegionTopics(_userRegion.value, getApplication())
        }
    }

    // Combine current feed articles with search query and category filtering
    val filteredArticles: StateFlow<List<NewsArticle>> = combine(
        feedState,
        searchQuery,
        selectedCategory,
        savedArticles
    ) { state, query, category, saved ->
        when (state) {
            is FeedState.Success -> {
                // First update the bookmark status of all fetched articles to match DB in real-time
                val savedLinks = saved.map { it.link }.toSet()
                val updatedArticles = state.articles.map { article ->
                    article.copy(isBookmarked = savedLinks.contains(article.link))
                }

                updatedArticles.filter { article ->
                    val matchesCategory = when (category) {
                        "All" -> true
                        "Finance" -> article.category.contains("Finance", ignoreCase = true) ||
                                     article.category.contains("Market", ignoreCase = true) ||
                                     article.category.contains("Business", ignoreCase = true) ||
                                     article.category.contains("Stock", ignoreCase = true) ||
                                     article.category.contains("Forex", ignoreCase = true) ||
                                     article.category.contains("Crypto", ignoreCase = true) ||
                                     article.category.contains("Economy", ignoreCase = true)
                        else -> article.category.equals(category, ignoreCase = true)
                    }
                    val matchesSearch = query.isEmpty() ||
                            article.title.contains(query, ignoreCase = true) ||
                            article.description.contains(query, ignoreCase = true) ||
                            article.sourceName.contains(query, ignoreCase = true)
                    matchesCategory && matchesSearch
                }
            }
            else -> emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshNews() {
        viewModelScope.launch {
            // Instantly load cached articles from Room database for seamless offline reading
            val cachedArticles = repository.getCachedNews()
            if (cachedArticles.isNotEmpty()) {
                _feedState.value = FeedState.Success(cachedArticles)
                if (_blindspotReport.value == null) {
                    runBlindspotAnalysis(cachedArticles)
                }
            } else {
                _feedState.value = FeedState.Loading
            }

            try {
                val articles = repository.fetchLiveNews()
                if (articles.isEmpty()) {
                    if (_feedState.value !is FeedState.Success) {
                        // Check if there are no enabled sources
                        val enabled = allSources.value.any { it.isEnabled }
                        if (!enabled) {
                            _feedState.value = FeedState.Error("No sources are enabled. Please enable some news platforms in the Sources tab.")
                        } else {
                            _feedState.value = FeedState.Error("Failed to fetch articles. Please check your network connection or try again.")
                        }
                    }
                } else {
                    _feedState.value = FeedState.Success(articles)
                    runBlindspotAnalysis(articles)
                }

            } catch (e: Exception) {
                if (_feedState.value !is FeedState.Success) {
                    _feedState.value = FeedState.Error("An unexpected error occurred: ${e.localizedMessage}")
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    // Source Interactions
    fun toggleSource(source: NewsSource) {
        viewModelScope.launch {
            repository.toggleSource(source)
            // Automatically refresh feed when sources change to immediately show/hide relevant content
            refreshNews()
        }
    }

    fun addCustomSource(name: String, url: String, category: String) {
        viewModelScope.launch {
            repository.addSource(name, url, category)
            refreshNews()
        }
    }

    fun deleteCustomSource(source: NewsSource) {
        viewModelScope.launch {
            repository.deleteCustomSource(source)
            refreshNews()
        }
    }

    // Bookmark Interactions
    fun toggleBookmark(article: NewsArticle) {
        viewModelScope.launch {
            repository.toggleBookmark(article)
        }
    }

    fun removeBookmark(link: String) {
        viewModelScope.launch {
            repository.removeBookmark(link)
        }
    }

    fun toggleDarkMode(systemDark: Boolean) {
        val current = _isDarkMode.value ?: systemDark
        val newValue = !current
        setThemeMode(newValue)
    }

    fun setThemeMode(mode: Boolean?) {
        _isDarkMode.value = mode
        val valueStr = when (mode) {
            false -> "light"
            true -> "dark"
            null -> "system"
        }
        prefs.edit().putString("theme_mode", valueStr).apply()
    }

    fun setTextSize(size: String) {
        _textSize.value = size
        prefs.edit().putString("text_size", size).apply()
    }

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        prefs.edit().putFloat("tts_speed", speed).apply()
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        prefs.edit().putFloat("tts_pitch", pitch).apply()
    }

    fun setVoiceTone(tone: String) {
        _selectedVoiceTone.value = tone
        prefs.edit().putString("voice_tone", tone).apply()
    }

    fun setAppIconVariation(variation: String) {
        _appIconVariation.value = variation
        
        val context = getApplication<Application>()
        val defaultAlias = "com.example.MainActivityDefault"
        val gold3DAlias = "com.example.MainActivityGold3D"
        val goldOutlineAlias = "com.example.MainActivityGoldOutline"
        val marbleAlias = "com.example.MainActivityMarble"
        
        val targetAlias = when (variation) {
            "Gold3D" -> gold3DAlias
            "GoldOutline" -> goldOutlineAlias
            "Marble" -> marbleAlias
            else -> defaultAlias
        }
        
        val aliasesToDisable = listOf(defaultAlias, gold3DAlias, goldOutlineAlias, marbleAlias).filter { it != targetAlias }
        val pm = context.packageManager
        
        try {
            // First enable the target alias
            pm.setComponentEnabledSetting(
                android.content.ComponentName(context, targetAlias),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            
            // Then disable other aliases
            for (alias in aliasesToDisable) {
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(context, alias),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateSummaryForArticle(article: NewsArticle) {
        val link = article.link
        if (_articleSummaries.value.containsKey(link)) return
        if (_summarizingStates.value[link] == true) return

        _summarizingStates.value = _summarizingStates.value + (link to true)
        viewModelScope.launch {
            try {
                val summary = com.example.data.network.GeminiSummarizer.generateSummary(article.title, article.description)
                _articleSummaries.value = _articleSummaries.value + (link to summary)
            } catch (e: Exception) {
                _articleSummaries.value = _articleSummaries.value + (link to "Error generating summary.")
            } finally {
                _summarizingStates.value = _summarizingStates.value + (link to false)
            }
        }
    }

    fun generateFullArticleForArticle(article: NewsArticle) {
        val link = article.link
        if (_fullArticles.value.containsKey(link)) return
        if (_generatingFullArticleStates.value[link] == true) return

        _generatingFullArticleStates.value = _generatingFullArticleStates.value + (link to true)
        viewModelScope.launch {
            try {
                val fullText = com.example.data.network.GeminiSummarizer.generateFullArticle(
                    title = article.title,
                    description = article.description,
                    sourceName = article.sourceName
                )
                _fullArticles.value = _fullArticles.value + (link to fullText)
            } catch (e: Exception) {
                _fullArticles.value = _fullArticles.value + (link to "Error generating full article.")
            } finally {
                _generatingFullArticleStates.value = _generatingFullArticleStates.value + (link to false)
            }
        }
    }

    fun addPortfolioItem(symbol: String, name: String, type: String, purchasePrice: Double, quantity: Double) {
        viewModelScope.launch {
            repository.insertPortfolioItem(
                com.example.data.model.PortfolioItem(
                    symbol = symbol.uppercase(),
                    name = name,
                    type = type,
                    purchasePrice = purchasePrice,
                    quantity = quantity
                )
            )
        }
    }

    fun deletePortfolioItem(id: Int) {
        viewModelScope.launch {
            repository.deletePortfolioItem(id)
        }
    }

    // --- Factum Intelligence Modules Methods ---

    // 1. BLINDSPOT_DETECTOR
    fun runBlindspotAnalysis(articles: List<NewsArticle>? = null) {
        val currentArticles = articles ?: (feedState.value as? FeedState.Success)?.articles ?: emptyList()
        _isBlindspotLoading.value = true
        viewModelScope.launch {
            try {
                val targetArticles = if (currentArticles.isNotEmpty()) {
                    currentArticles
                } else {
                    repository.getCachedNews()
                }

                if (targetArticles.isEmpty()) {
                    // Fallback default report if no articles available at all
                    _blindspotReport.value = com.example.data.model.BlindspotReport(
                        headline = "Regional Trade & Regulatory Shifts",
                        alertText = "Story coverage for ${_userRegion.value} is concentrated in regional portals but under-represented in global wire services.",
                        trendingRegion = _userRegion.value,
                        overlookedRegion = "Global Media Feeds",
                        missingContext = "Localized economic policies, regulatory updates, and supply chain updates in ${_userRegion.value} are receiving primary coverage in regional outlets.",
                        topSource = "Regional Feeds"
                    )
                } else {
                    val report = com.example.data.network.FactumIntelligenceEngine.detectBlindspots(targetArticles, _userRegion.value)
                    _blindspotReport.value = report
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val sample = currentArticles.firstOrNull()
                _blindspotReport.value = com.example.data.model.BlindspotReport(
                    headline = sample?.title ?: "Regional Market & Policy Updates",
                    alertText = "This story is trending heavily in ${_userRegion.value} regional feeds with lower visibility in mainstream global feeds.",
                    trendingRegion = _userRegion.value,
                    overlookedRegion = "Global Media Feeds",
                    missingContext = "Key regulatory and economic developments in ${_userRegion.value} reported by ${sample?.sourceName ?: "regional sources"} have missing context in global wire feeds.",
                    topSource = sample?.sourceName ?: "Regional Feeds"
                )
            } finally {
                _isBlindspotLoading.value = false
            }
        }
    }

    // 2. CATCH_ME_UP
    fun generateCatchMeUp(article: NewsArticle) {
        val link = article.link
        if (_catchMeUpTimelines.value.containsKey(link)) return
        if (_catchMeUpLoading.value[link] == true) return

        _catchMeUpLoading.value = _catchMeUpLoading.value + (link to true)
        viewModelScope.launch {
            try {
                val timeline = com.example.data.network.FactumIntelligenceEngine.generateCatchMeUp(article)
                _catchMeUpTimelines.value = _catchMeUpTimelines.value + (link to timeline)
            } catch (e: Exception) {
                _catchMeUpTimelines.value = _catchMeUpTimelines.value + (link to "Unable to generate backstory timeline.")
            } finally {
                _catchMeUpLoading.value = _catchMeUpLoading.value + (link to false)
            }
        }
    }

    // 3. INTERACTIVE_ARTICLE_CHAT
    fun sendArticleChatMessage(article: NewsArticle, question: String) {
        if (question.isBlank()) return
        val link = article.link
        val currentList = _articleChats.value[link] ?: emptyList()
        val userMsg = com.example.data.model.ArticleChatMessage(sender = "user", message = question)
        val updatedList = currentList + userMsg
        _articleChats.value = _articleChats.value + (link to updatedList)

        _chatLoading.value = _chatLoading.value + (link to true)
        val fullText = fullArticles.value[link]

        viewModelScope.launch {
            try {
                val aiAnswer = com.example.data.network.FactumIntelligenceEngine.answerArticleQuestion(
                    article = article,
                    fullArticleText = fullText,
                    question = question
                )
                val aiMsg = com.example.data.model.ArticleChatMessage(sender = "ai", message = aiAnswer)
                _articleChats.value = _articleChats.value + (link to (_articleChats.value[link] ?: emptyList()) + aiMsg)
            } catch (e: Exception) {
                val errorMsg = com.example.data.model.ArticleChatMessage(sender = "ai", message = "This detail isn't mentioned in the article.")
                _articleChats.value = _articleChats.value + (link to (_articleChats.value[link] ?: emptyList()) + errorMsg)
            } finally {
                _chatLoading.value = _chatLoading.value + (link to false)
            }
        }
    }

    fun clearArticleChat(articleLink: String) {
        _articleChats.value = _articleChats.value - articleLink
    }

    // 4. REALTIME_NUANCED_TRANSLATION
    fun generateNuancedTranslation(article: NewsArticle) {
        val link = article.link
        if (_nuancedTranslations.value.containsKey(link)) return
        if (_translationLoading.value[link] == true) return

        _translationLoading.value = _translationLoading.value + (link to true)
        viewModelScope.launch {
            try {
                val translation = com.example.data.network.FactumIntelligenceEngine.generateNuancedTranslation(article)
                _nuancedTranslations.value = _nuancedTranslations.value + (link to translation)
            } catch (e: Exception) {
                _nuancedTranslations.value = _nuancedTranslations.value + (link to "Translation unavailable.")
            } finally {
                _translationLoading.value = _translationLoading.value + (link to false)
            }
        }
    }

    // 5. LOCAL_IMPACT_ANALYSIS
    fun setUserRegion(region: String) {
        _userRegion.value = region
        prefs.edit().putString("user_region", region).apply()
        // Clear cached impacts so they regenerate for new region
        _localImpacts.value = emptyMap()
        // Sync FCM topic for newly selected region
        com.example.util.NotificationHelper.syncFcmRegionTopics(region)
        // Re-run blindspot analysis for new region
        runBlindspotAnalysis()
    }

    fun triggerBreakingNewsNotification(context: Context, article: NewsArticle) {
        com.example.util.NotificationHelper.sendBreakingNewsNotification(
            context = context,
            title = article.title,
            body = article.description,
            articleUrl = article.link,
            region = _userRegion.value
        )
    }

    fun simulatePersonalizedPushNotification(context: Context) {
        val region = _userRegion.value
        val sector = _userSector.value
        val sampleArticle = (feedState.value as? FeedState.Success)?.articles?.firstOrNull()

        val pushTitle = if (sampleArticle != null) {
            "BREAKING: ${sampleArticle.title}"
        } else {
            "BREAKING: Key Economic & Policy Alert for $region ($sector)"
        }

        val pushBody = if (sampleArticle != null) {
            "Personalized alert for $region: ${sampleArticle.description}"
        } else {
            "Major developments detected in $region impacting $sector markets. Tap for full Intelligence Briefing."
        }

        com.example.util.NotificationHelper.sendBreakingNewsNotification(
            context = context,
            title = pushTitle,
            body = pushBody,
            articleUrl = sampleArticle?.link,
            region = region
        )
    }

    fun setUserSector(sector: String) {
        _userSector.value = sector
        prefs.edit().putString("user_sector", sector).apply()
        // Clear cached impacts so they regenerate for new sector
        _localImpacts.value = emptyMap()
    }

    fun generateLocalImpact(article: NewsArticle) {
        val link = article.link
        if (_localImpacts.value.containsKey(link)) return
        if (_localImpactLoading.value[link] == true) return

        _localImpactLoading.value = _localImpactLoading.value + (link to true)
        viewModelScope.launch {
            try {
                val impact = com.example.data.network.FactumIntelligenceEngine.generateLocalImpactAnalysis(
                    article = article,
                    userRegion = _userRegion.value,
                    userSector = _userSector.value
                )
                _localImpacts.value = _localImpacts.value + (link to impact)
            } catch (e: Exception) {
                _localImpacts.value = _localImpacts.value + (link to "Why This Matters to You:\nLocal impact analysis currently unavailable.")
            } finally {
                _localImpactLoading.value = _localImpactLoading.value + (link to false)
            }
        }
    }
}

