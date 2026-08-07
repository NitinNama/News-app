package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.NewsArticle
import com.example.data.model.NewsSource
import com.example.data.model.SavedArticle
import com.example.ui.viewmodel.FeedState
import com.example.ui.viewmodel.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: NewsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("Feed") } // "Feed", "Sources", "Bookmarks", "Settings"

    val feedState by viewModel.feedState.collectAsState()
    val filteredArticles by viewModel.filteredArticles.collectAsState()
    val allSources by viewModel.allSources.collectAsState()
    val savedArticles by viewModel.savedArticles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val customDarkMode by viewModel.isDarkMode.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkModeActive = customDarkMode ?: systemDark

    val articleSummaries by viewModel.articleSummaries.collectAsState()
    val summarizingStates by viewModel.summarizingStates.collectAsState()

    val fullArticles by viewModel.fullArticles.collectAsState()
    val generatingFullArticles by viewModel.generatingFullArticleStates.collectAsState()
    val allPortfolioItems by viewModel.allPortfolioItems.collectAsState()
    val marketQuotes by viewModel.marketQuotes.collectAsState()

    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val selectedVoiceTone by viewModel.selectedVoiceTone.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val appIconVariation by viewModel.appIconVariation.collectAsState()

    // Factum Intelligence States
    val blindspotReport by viewModel.blindspotReport.collectAsState()
    val isBlindspotLoading by viewModel.isBlindspotLoading.collectAsState()
    val catchMeUpTimelines by viewModel.catchMeUpTimelines.collectAsState()
    val catchMeUpLoading by viewModel.catchMeUpLoading.collectAsState()
    val articleChats by viewModel.articleChats.collectAsState()
    val chatLoading by viewModel.chatLoading.collectAsState()
    val nuancedTranslations by viewModel.nuancedTranslations.collectAsState()
    val translationLoading by viewModel.translationLoading.collectAsState()
    val userRegion by viewModel.userRegion.collectAsState()
    val userSector by viewModel.userSector.collectAsState()
    val localImpacts by viewModel.localImpacts.collectAsState()
    val localImpactLoading by viewModel.localImpactLoading.collectAsState()

    var showAddPortfolioDialog by remember { mutableStateOf(false) }


    // Android TextToSpeech Setup
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }

    DisposableEffect(context) {
        var speech: android.speech.tts.TextToSpeech? = null
        speech = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                speech?.let { s ->
                    val langResult = s.setLanguage(java.util.Locale.US)
                    if (langResult == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langResult == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                        s.setLanguage(java.util.Locale.getDefault())
                    }
                    
                    // Select highest quality or natural-sounding voice if available
                    try {
                        val voices = s.voices
                        if (!voices.isNullOrEmpty()) {
                            val bestVoice = voices.firstOrNull { voice: android.speech.tts.Voice ->
                                voice.locale.language == java.util.Locale.US.language &&
                                voice.quality >= android.speech.tts.Voice.QUALITY_HIGH
                            } ?: voices.firstOrNull { voice: android.speech.tts.Voice ->
                                voice.locale.language == java.util.Locale.US.language
                            } ?: voices.firstOrNull { voice: android.speech.tts.Voice ->
                                voice.quality >= android.speech.tts.Voice.QUALITY_HIGH
                            }
                            bestVoice?.let { s.voice = it }
                        }
                    } catch (e: Exception) {
                        // Fallback to default
                    }
                }
            }
        }
        speech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isTtsSpeaking = true
            }
            override fun onDone(utteranceId: String?) {
                isTtsSpeaking = false
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isTtsSpeaking = false
            }
        })
        tts = speech
        onDispose {
            speech?.stop()
            speech?.shutdown()
        }
    }

    val onSpeakClick: (NewsArticle) -> Unit = { article ->
        tts?.let { speech ->
            if (isTtsSpeaking) {
                speech.stop()
                isTtsSpeaking = false
            } else {
                // Dynamically apply human-like voice adjustments based on profile presets and fine sliders
                val basePitch = when (selectedVoiceTone) {
                    "Warm" -> 0.88f // Warm, deeper resonance
                    "Crisp" -> 1.12f // Clear, bright, crisp
                    else -> 1.00f   // Natural standard
                }
                val baseSpeed = when (selectedVoiceTone) {
                    "Warm" -> 0.94f // Slightly slower and more deliberate
                    "Crisp" -> 1.06f // Brisk and professional
                    else -> 1.00f   // Natural standard
                }

                speech.setPitch(basePitch * ttsPitch)
                speech.setSpeechRate(baseSpeed * ttsSpeed)

                val fullText = fullArticles[article.link]
                val summaryText = articleSummaries[article.link] ?: ""
                
                // Construct the text content
                val textToRead = if (fullText != null) {
                    "Now reading the full investigative article: ${article.title}. Published by ${article.sourceName}. $fullText"
                } else {
                    "${article.title}. Published by ${article.sourceName}. ${article.description}. ${if (summaryText.isNotEmpty()) "AI Summary is: $summaryText" else ""}"
                }
                
                speech.speak(textToRead, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "news_aggregation_speak")
                isTtsSpeaking = true
            }
        }
    }

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var selectedArticleForDetail by remember { mutableStateOf<NewsArticle?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            NewsTopAppBar(
                currentTab = currentTab,
                searchQuery = searchQuery,
                onSearchChanged = { viewModel.setSearchQuery(it) },
                onRefresh = { viewModel.refreshNews() },
                isLoading = feedState is FeedState.Loading,
                onAddSourceClick = {
                    if (currentTab == "Portfolio") {
                        showAddPortfolioDialog = true
                    } else {
                        showAddSourceDialog = true
                    }
                },
                isDarkModeActive = isDarkModeActive,
                onToggleDarkMode = { viewModel.toggleDarkMode(systemDark) },
                onNotificationPushClick = {
                    viewModel.simulatePersonalizedPushNotification(context)
                    android.widget.Toast.makeText(context, "⚡ Personalized Breaking News Push Triggered!", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        },
        bottomBar = {
            NewsBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "Feed" -> {
                    FeedTabContent(
                        feedState = feedState,
                        articles = filteredArticles,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                        onArticleClick = { selectedArticleForDetail = it },
                        onBookmarkToggle = { viewModel.toggleBookmark(it) },
                        onShareClick = { shareArticle(context, it) },
                        onRetry = { viewModel.refreshNews() },
                        articleSummaries = articleSummaries,
                        summarizingStates = summarizingStates,
                        onGenerateSummary = { viewModel.generateSummaryForArticle(it) },
                        marketQuotes = marketQuotes,
                        blindspotReport = blindspotReport,
                        isBlindspotLoading = isBlindspotLoading,
                        userRegion = userRegion,
                        onRescanBlindspot = { viewModel.runBlindspotAnalysis() }
                    )
                }

                "Sources" -> {
                    SourcesTabContent(
                        sources = allSources,
                        onToggleSource = { viewModel.toggleSource(it) },
                        onDeleteSource = { viewModel.deleteCustomSource(it) }
                    )
                }
                "Bookmarks" -> {
                    BookmarksTabContent(
                        savedArticles = savedArticles,
                        onArticleClick = { saved ->
                            selectedArticleForDetail = NewsArticle(
                                title = saved.title,
                                link = saved.link,
                                description = saved.description,
                                pubDate = saved.pubDate,
                                sourceName = saved.sourceName,
                                category = saved.category,
                                imageUrl = saved.imageUrl,
                                isBookmarked = true
                            )
                        },
                        onRemoveBookmark = { viewModel.removeBookmark(it.link) },
                        onShareClick = { saved ->
                            shareArticle(
                                context,
                                NewsArticle(
                                    title = saved.title,
                                    link = saved.link,
                                    description = saved.description,
                                    pubDate = saved.pubDate,
                                    sourceName = saved.sourceName,
                                    category = saved.category,
                                    imageUrl = saved.imageUrl,
                                    isBookmarked = true
                                )
                            )
                        }
                    )
                }
                "Portfolio" -> {
                    PortfolioTabContent(
                        portfolioItems = allPortfolioItems,
                        marketQuotes = marketQuotes,
                        onDeleteItem = { viewModel.deletePortfolioItem(it.id) },
                        onAddItem = { symbol, name, type, price, qty ->
                            viewModel.addPortfolioItem(symbol, name, type, price, qty)
                        }
                    )
                }
                "Settings" -> {
                    SettingsTabContent(
                        viewModel = viewModel,
                        isDarkModeActive = isDarkModeActive,
                        customDarkMode = customDarkMode,
                        textSize = textSize,
                        ttsSpeed = ttsSpeed,
                        ttsPitch = ttsPitch,
                        selectedVoiceTone = selectedVoiceTone,
                        savedArticlesCount = savedArticles.size,
                        customSourcesCount = allSources.filter { it.isCustom }.size,
                        portfolioItemsCount = allPortfolioItems.size,
                        appIconVariation = appIconVariation
                    )
                }
            }

            // Article Detail Bottom Sheet
            selectedArticleForDetail?.let { article ->
                ArticleDetailSheet(
                    article = article,
                    viewModel = viewModel,
                    onDismiss = {
                        tts?.stop()
                        isTtsSpeaking = false
                        selectedArticleForDetail = null
                    },
                    onBookmarkToggle = { viewModel.toggleBookmark(article) },
                    onShareClick = { shareArticle(context, article) },
                    onOpenInBrowser = { openArticleUrl(context, article.link) },
                    articleSummaries = articleSummaries,
                    summarizingStates = summarizingStates,
                    onGenerateSummary = { viewModel.generateSummaryForArticle(article) },
                    isTtsSpeaking = isTtsSpeaking,
                    onSpeakClick = { onSpeakClick(article) },
                    fullArticles = fullArticles,
                    generatingFullArticles = generatingFullArticles,
                    onGenerateFullArticle = { viewModel.generateFullArticleForArticle(article) },
                    ttsSpeed = ttsSpeed,
                    onSpeedChange = { viewModel.setTtsSpeed(it) },
                    ttsPitch = ttsPitch,
                    onPitchChange = { viewModel.setTtsPitch(it) },
                    selectedVoiceTone = selectedVoiceTone,
                    onToneChange = { viewModel.setVoiceTone(it) },
                    textSize = textSize,
                    catchMeUpTimelines = catchMeUpTimelines,
                    catchMeUpLoading = catchMeUpLoading,
                    articleChats = articleChats,
                    chatLoading = chatLoading,
                    nuancedTranslations = nuancedTranslations,
                    translationLoading = translationLoading,
                    userRegion = userRegion,
                    userSector = userSector,
                    localImpacts = localImpacts,
                    localImpactLoading = localImpactLoading
                )
            }


            // Add Custom Source Dialog
            if (showAddSourceDialog) {
                AddCustomSourceDialog(
                    onDismiss = { showAddSourceDialog = false },
                    onAddSource = { name, url, category ->
                        viewModel.addCustomSource(name, url, category)
                        showAddSourceDialog = false
                    }
                )
            }

            // Add Custom Portfolio Dialog
            if (showAddPortfolioDialog) {
                AddPortfolioItemDialog(
                    onDismiss = { showAddPortfolioDialog = false },
                    onAddItem = { symbol, name, type, price, qty ->
                        viewModel.addPortfolioItem(symbol, name, type, price, qty)
                        showAddPortfolioDialog = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsTopAppBar(
    currentTab: String,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    onAddSourceClick: () -> Unit,
    isDarkModeActive: Boolean,
    onToggleDarkMode: () -> Unit,
    onNotificationPushClick: () -> Unit = {}
) {
    var isSearching by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onToggleDarkMode, modifier = Modifier.testTag("dark_mode_toggle")) {
                Icon(
                    imageVector = if (isDarkModeActive) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            if (isSearching && currentTab == "Feed") {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search articles...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input")
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (currentTab == "Feed") {
                        Text(
                            text = "Pulse.",
                            fontFamily = FontFamily.Serif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = (-1).sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = when (currentTab) {
                                "Sources" -> "PLATFORMS"
                                "Bookmarks" -> "SAVED STORIES"
                                "Portfolio" -> "PORTFOLIO"
                                "Settings" -> "SETTINGS"
                                else -> "NEWS"
                            },
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        actions = {
            if (currentTab == "Feed") {
                IconButton(onClick = {
                    isSearching = !isSearching
                    if (!isSearching) onSearchChanged("")
                }, modifier = Modifier.testTag("search_toggle_button")) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.testTag("refresh_button")) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                IconButton(
                    onClick = onNotificationPushClick,
                    modifier = Modifier.testTag("push_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Push Notifications",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (currentTab == "Sources" || currentTab == "Portfolio") {
                IconButton(onClick = onAddSourceClick, modifier = Modifier.testTag("add_source_button")) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun NewsBottomNavigation(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        modifier = Modifier.border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        NavigationBarItem(
            selected = currentTab == "Feed",
            onClick = { onTabSelected("Feed") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "Feed") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Feed"
                )
            },
            label = { Text("Feed") },
            modifier = Modifier.testTag("nav_tab_feed")
        )
        NavigationBarItem(
            selected = currentTab == "Sources",
            onClick = { onTabSelected("Sources") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "Sources") Icons.Filled.List else Icons.Outlined.ListAlt,
                    contentDescription = "Sources"
                )
            },
            label = { Text("Sources") },
            modifier = Modifier.testTag("nav_tab_sources")
        )
        NavigationBarItem(
            selected = currentTab == "Bookmarks",
            onClick = { onTabSelected("Bookmarks") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "Bookmarks") Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmarks"
                )
            },
            label = { Text("Saved") },
            modifier = Modifier.testTag("nav_tab_saved")
        )
        NavigationBarItem(
            selected = currentTab == "Portfolio",
            onClick = { onTabSelected("Portfolio") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "Portfolio") Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp,
                    contentDescription = "Portfolio"
                )
            },
            label = { Text("Portfolio") },
            modifier = Modifier.testTag("nav_tab_portfolio")
        )
        NavigationBarItem(
            selected = currentTab == "Settings",
            onClick = { onTabSelected("Settings") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "Settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings") },
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}

@Composable
fun MarketTickerRow(
    marketQuotes: Map<String, com.example.data.network.MarketQuote> = emptyMap()
) {
    val defaultItems = listOf(
        MarketItem("SENSEX", "79,200.50", "+0.45%", true),
        MarketItem("NIFTY 50", "24,150.80", "+0.52%", true),
        MarketItem("AAPL", "$178.44", "+1.12%", true),
        MarketItem("NVDA", "$875.12", "+4.56%", true),
        MarketItem("GOOGL", "$152.50", "+0.85%", true),
        MarketItem("TSLA", "$171.05", "-2.41%", false),
        MarketItem("EUR/USD", "1.0845", "+0.24%", true),
        MarketItem("GBP/USD", "1.2682", "-0.15%", false),
        MarketItem("BTC/USD", "$64,250", "+1.80%", true),
        MarketItem("ETH/USD", "$3,415", "+0.95%", true)
    )

    val displayItems = if (marketQuotes.isNotEmpty()) {
        marketQuotes.values.map { q ->
            val formattedPrice = when {
                q.symbol == "SENSEX" || q.symbol == "NIFTY 50" || q.symbol.contains("NIFTY") -> {
                    String.format("%,.2f", q.currentPrice)
                }
                q.symbol.contains("/") && !q.symbol.startsWith("BTC") -> {
                    String.format("%.4f", q.currentPrice)
                }
                q.symbol.startsWith("BTC") -> {
                    "$${String.format("%,.0f", q.currentPrice)}"
                }
                else -> {
                    "$${String.format("%.2f", q.currentPrice)}"
                }
            }
            val formattedChange = String.format("%+.2f%%", q.changePercent)
            MarketItem(
                symbol = q.symbol,
                price = formattedPrice,
                change = formattedChange,
                isUp = q.changePercent >= 0
            )
        }
    } else {
        defaultItems
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .testTag("market_ticker_row")
    ) {
        items(displayItems) { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = item.symbol,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.price,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.change,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isUp) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
        }
    }
}

data class MarketItem(
    val symbol: String,
    val price: String,
    val change: String,
    val isUp: Boolean
)

@Composable
fun FeedTabContent(
    feedState: FeedState,
    articles: List<NewsArticle>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    onBookmarkToggle: (NewsArticle) -> Unit,
    onShareClick: (NewsArticle) -> Unit,
    onRetry: () -> Unit,
    articleSummaries: Map<String, String>,
    summarizingStates: Map<String, Boolean>,
    onGenerateSummary: (NewsArticle) -> Unit,
    marketQuotes: Map<String, com.example.data.network.MarketQuote> = emptyMap(),
    blindspotReport: com.example.data.model.BlindspotReport? = null,
    isBlindspotLoading: Boolean = false,
    userRegion: String = "Global",
    onRescanBlindspot: () -> Unit = {}
) {
    val categories = listOf("All", "Finance", "Stock Market", "Forex Market", "Crypto", "Business", "World", "Asia", "Europe", "Middle East", "Latin America", "Russia", "India", "China", "Technology", "Science", "Sports")

    Column(modifier = Modifier.fillMaxSize()) {
        MarketTickerRow(marketQuotes = marketQuotes)
        // Horizontal category selector
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }

        // Main content list or status
        when (feedState) {
            is FeedState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Aggregating latest news from world platforms...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            is FeedState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = feedState.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
            is FeedState.Success -> {
                if (articles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Newspaper,
                                contentDescription = "No news",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No articles found matching filters.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(minSize = 280.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalItemSpacing = 16.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. BLINDSPOT_DETECTOR Card (Top of Feed)
                        item(span = StaggeredGridItemSpan.FullLine) {
                            BlindspotAlertCard(
                                report = blindspotReport,
                                isLoading = isBlindspotLoading,
                                userRegion = userRegion,
                                onRescan = onRescanBlindspot
                            )
                        }

                        // Top Story / Hero Card (Only if "All" is selected and we have an image article)
                        val heroArticle = articles.firstOrNull { it.imageUrl != null }
                        if (heroArticle != null && selectedCategory == "All") {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                HeroArticleCard(
                                    article = heroArticle,
                                    onClick = { onArticleClick(heroArticle) },
                                    onBookmarkToggle = { onBookmarkToggle(heroArticle) },
                                    onShareClick = { onShareClick(heroArticle) },
                                    summary = articleSummaries[heroArticle.link],
                                    isSummarizing = summarizingStates[heroArticle.link] == true,
                                    onSummarizeClick = { onGenerateSummary(heroArticle) }
                                )
                            }
                        }


                        // Normal Articles
                        val listArticles = if (heroArticle != null && selectedCategory == "All") {
                            articles.filter { it.link != heroArticle.link }
                        } else {
                            articles
                        }

                        items(listArticles) { article ->
                            ArticleGridCard(
                                article = article,
                                onClick = { onArticleClick(article) },
                                onBookmarkToggle = { onBookmarkToggle(article) },
                                onShareClick = { onShareClick(article) },
                                summary = articleSummaries[article.link],
                                isSummarizing = summarizingStates[article.link] == true,
                                onSummarizeClick = { onGenerateSummary(article) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroArticleCard(
    article: NewsArticle,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShareClick: () -> Unit,
    summary: String?,
    isSummarizing: Boolean,
    onSummarizeClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp), // More modern, highly rounded corner as in Tailwind rounded-3xl
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("hero_article_card")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp) // Taller, magnificent hero banner
            ) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay Gradient at the bottom of the image for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                startY = 100f
                            )
                        )
                )

                // Source Name and Category badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = article.sourceName.uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = article.category.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = article.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, // Distinctly larger headline
                    lineHeight = 28.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = article.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // AI Inline Summary Section for Hero
                if (summary != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AI BRIEF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (isSummarizing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Synthesizing AI Summary...",
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSummarizeClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "AI Quick Summary",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.pubDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )

                    Row {
                        IconButton(onClick = onShareClick) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onBookmarkToggle) {
                            Icon(
                                imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleRowItem(
    article: NewsArticle,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShareClick: () -> Unit,
    summary: String?,
    isSummarizing: Boolean,
    onSummarizeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("article_row_item")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (article.category == "Technology" || article.category == "Science") Color(0xFF3B82F6) else Color(0xFFEF4444),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                    Text(
                        text = article.sourceName.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = article.category.uppercase(),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = article.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.pubDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                // Inline AI summary row
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (summary != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "AI SUMMARY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = summary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isSummarizing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Synthesizing AI summary...",
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSummarizeClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "AI Summary",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ArticleGridCard(
    article: NewsArticle,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShareClick: () -> Unit,
    summary: String?,
    isSummarizing: Boolean,
    onSummarizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("article_grid_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header Metas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (article.category == "Technology" || article.category == "Science") Color(0xFF3B82F6) else Color(0xFFEF4444),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                    Text(
                        text = article.sourceName.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "•",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = article.category.uppercase(),
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Title
                Text(
                    text = article.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (article.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = article.description,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // PubDate
                Text(
                    text = article.pubDate,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // AI Summary Area (staggered or inline block)
                if (summary != null || isSummarizing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp)
                    ) {
                        if (summary != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "AI SUMMARY",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = summary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isSummarizing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Synthesizing summary...",
                                    fontSize = 10.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Actions Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Quick Action: Summarize if not already generated
                    if (summary == null && !isSummarizing) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable { onSummarizeClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "AI Summary",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        // Spacer to push share and bookmark to the right
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onBookmarkToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (article.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcesTabContent(
    sources: List<NewsSource>,
    onToggleSource: (NewsSource) -> Unit,
    onDeleteSource: (NewsSource) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Aggregated Platforms",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Enable or disable feeds, or add your custom platforms.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sources) { source ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (source.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("source_card_${source.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = source.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (source.isCustom) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "USER",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Category: ${source.category}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = source.url,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = source.isEnabled,
                                onCheckedChange = { onToggleSource(source) },
                                modifier = Modifier.testTag("source_switch_${source.id}")
                            )
                            if (source.isCustom) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onDeleteSource(source) },
                                    modifier = Modifier.testTag("delete_source_button_${source.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete custom source",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksTabContent(
    savedArticles: List<SavedArticle>,
    onArticleClick: (SavedArticle) -> Unit,
    onRemoveBookmark: (SavedArticle) -> Unit,
    onShareClick: (SavedArticle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Saved Stories",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "These articles are fully available offline.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (savedArticles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Empty Bookmarks",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No saved articles yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedArticles) { saved ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArticleClick(saved) }
                            .testTag("saved_article_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = saved.sourceName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = "•", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        text = saved.category,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = saved.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = saved.pubDate,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Row {
                                        IconButton(onClick = { onShareClick(saved) }) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { onRemoveBookmark(saved) }) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkRemove,
                                                contentDescription = "Unbookmark",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (saved.imageUrl != null) {
                                AsyncImage(
                                    model = saved.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailSheet(
    article: NewsArticle,
    viewModel: NewsViewModel? = null,
    onDismiss: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShareClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    articleSummaries: Map<String, String>,
    summarizingStates: Map<String, Boolean>,
    onGenerateSummary: () -> Unit,
    isTtsSpeaking: Boolean,
    onSpeakClick: () -> Unit,
    fullArticles: Map<String, String>,
    generatingFullArticles: Map<String, Boolean>,
    onGenerateFullArticle: () -> Unit,
    ttsSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    ttsPitch: Float,
    onPitchChange: (Float) -> Unit,
    selectedVoiceTone: String,
    onToneChange: (String) -> Unit,
    textSize: String = "Medium",
    catchMeUpTimelines: Map<String, String> = emptyMap(),
    catchMeUpLoading: Map<String, Boolean> = emptyMap(),
    articleChats: Map<String, List<com.example.data.model.ArticleChatMessage>> = emptyMap(),
    chatLoading: Map<String, Boolean> = emptyMap(),
    nuancedTranslations: Map<String, String> = emptyMap(),
    translationLoading: Map<String, Boolean> = emptyMap(),
    userRegion: String = "South Asia",
    userSector: String = "Finance & Markets",
    localImpacts: Map<String, String> = emptyMap(),
    localImpactLoading: Map<String, Boolean> = emptyMap()
) {

    val dynamicFontSize = when (textSize) {
        "Small" -> 13.sp
        "Large" -> 18.sp
        else -> 15.sp
    }
    val dynamicLineHeight = when (textSize) {
        "Small" -> 19.sp
        "Large" -> 26.sp
        else -> 22.sp
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .testTag("article_detail_sheet")
        ) {
            // Source & Tag Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = article.sourceName.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = article.title,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date
            Text(
                text = "Published: ${article.pubDate}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Large Image if available
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Description / Snippet
            Text(
                text = article.description,
                fontSize = dynamicFontSize,
                lineHeight = dynamicLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Full Article Reader Section
            val fullText = fullArticles[article.link]
            val isGeneratingFull = generatingFullArticles[article.link] == true

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "READER MODE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (fullText != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AI Full Journal Report",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = fullText,
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
            } else if (isGeneratingFull) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Synthesizing full investigation report...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Gemini is analyzing sources to reconstruct a detailed article",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGenerateFullArticle() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Read Full AI-Generated Article",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reconstruct full-text journalistic article from snippet",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Summary box
            val summary = articleSummaries[article.link]
            val isSummarizing = summarizingStates[article.link] == true

            if (summary != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI GENERATED ARTICLE SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (isSummarizing) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Analyzing with Gemini AI models...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGenerateSummary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate AI Summary", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto Reader Option Block with Custom Voice Presets and Adjustments
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row with Icon, Info and Speak Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isTtsSpeaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTtsSpeaking) Icons.Default.VolumeUp else Icons.Default.Hearing,
                                    contentDescription = "Speaker icon",
                                    tint = if (isTtsSpeaking) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI Auto-Reader Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (isTtsSpeaking) "Narrating now..." else "Premium Human-like Synthesis",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Button(
                            onClick = onSpeakClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTtsSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                contentColor = if (isTtsSpeaking) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTtsSpeaking) "Stop" else "Listen", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))

                    // Voice Tone Selector Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "VOICE PROFILE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Warm", "🎙️ Warm", "Deep, calm, natural"),
                                Triple("Crisp", "📢 Crisp", "Clear, professional"),
                                Triple("Natural", "👤 Default", "Standard accent")
                            ).forEach { (id, label, desc) ->
                                val isSelected = selectedVoiceTone == id
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onToneChange(id) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Pitch and speed sliders
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Playback Speed Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SPEED RATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${String.format("%.2f", ttsSpeed)}x",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Slider(
                                value = ttsSpeed,
                                onValueChange = onSpeedChange,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // Voice Pitch Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VOICE PITCH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${String.format("%.2f", ttsPitch)}x",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Slider(
                                value = ttsPitch,
                                onValueChange = onPitchChange,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FACTUM INTELLIGENCE MODULES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 5. LOCAL_IMPACT_ANALYSIS Section
            LocalImpactSection(
                article = article,
                userRegion = userRegion,
                userSector = userSector,
                impactText = localImpacts[article.link],
                isLoading = localImpactLoading[article.link] == true,
                onGenerate = { viewModel?.generateLocalImpact(article) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. CATCH_ME_UP Section
            CatchMeUpSection(
                article = article,
                timelineText = catchMeUpTimelines[article.link],
                isLoading = catchMeUpLoading[article.link] == true,
                onGenerate = { viewModel?.generateCatchMeUp(article) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. REALTIME_NUANCED_TRANSLATION Section
            RealtimeNuancedTranslationSection(
                article = article,
                translatedText = nuancedTranslations[article.link],
                isLoading = translationLoading[article.link] == true,
                onGenerate = { viewModel?.generateNuancedTranslation(article) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. INTERACTIVE_ARTICLE_CHAT Section
            InteractiveArticleChatSection(
                article = article,
                messages = articleChats[article.link] ?: emptyList(),
                isLoading = chatLoading[article.link] == true,
                onSendMessage = { q -> viewModel?.sendArticleChatMessage(article, q) },
                onClearChat = { viewModel?.clearArticleChat(article.link) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Actions Block

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.weight(1f).testTag("sheet_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (article.isBookmarked) "Saved" else "Save Offline")
                }

                OutlinedButton(
                    onClick = onShareClick,
                    modifier = Modifier.weight(1f).testTag("sheet_share_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Read original Article Button
            Button(
                onClick = onOpenInBrowser,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_open_web_button")
            ) {
                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Original Article on Website", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AddCustomSourceDialog(
    onDismiss: () -> Unit,
    onAddSource: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("World") }
    val categories = listOf("Finance", "Stock Market", "Forex Market", "Crypto", "Business", "World", "Asia", "Europe", "Middle East", "Latin America", "Russia", "India", "China", "Technology", "Science", "Sports")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom RSS Platform", fontWeight = FontWeight.BoldFamily) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Platform Name") },
                    placeholder = { Text("e.g., My Favorite Tech Blog") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_source_name")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RSS Feed URL") },
                    placeholder = { Text("https://example.com/feed/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("add_source_url")
                )

                Column {
                    Text("Select Category", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val selected = cat == selectedCategory
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                                modifier = Modifier.testTag("add_source_cat_$cat")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onAddSource(name.trim(), url.trim(), selectedCategory)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_source")
            ) {
                Text("Add Platform")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Utility functions
fun shareArticle(context: Context, article: NewsArticle) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more at: ${article.link}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Article Link"))
}

fun openArticleUrl(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open URL in browser.", Toast.LENGTH_SHORT).show()
    }
}

data class QuickAsset(
    val symbol: String,
    val name: String,
    val type: String, // "STOCK" or "CURRENCY"
    val defaultPrice: Double
)

@Composable
fun QuickAddSuggestionsRow(
    portfolioItems: List<com.example.data.model.PortfolioItem>,
    onAddItem: (symbol: String, name: String, type: String, price: Double, qty: Double) -> Unit
) {
    val trackedSymbols = remember(portfolioItems) {
        portfolioItems.map { it.symbol.uppercase() }.toSet()
    }

    val popularSuggestions = listOf(
        QuickAsset("AAPL", "Apple Inc.", "STOCK", 185.50),
        QuickAsset("TSLA", "Tesla Inc.", "STOCK", 220.80),
        QuickAsset("NVDA", "NVIDIA Corp.", "STOCK", 450.25),
        QuickAsset("MSFT", "Microsoft Corp.", "STOCK", 380.15),
        QuickAsset("BTC", "Bitcoin", "CURRENCY", 65200.00),
        QuickAsset("EUR/USD", "Euro / USD", "CURRENCY", 1.085),
        QuickAsset("GBP/USD", "Pound / USD", "CURRENCY", 1.272),
        QuickAsset("USD/INR", "USD / Rupee", "CURRENCY", 83.54)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "QUICK-TRACK POPULAR ASSETS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(popularSuggestions) { asset ->
                val isTracked = trackedSymbols.contains(asset.symbol.uppercase())
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTracked) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        }
                    ),
                    modifier = Modifier
                        .width(140.dp)
                        .clickable(enabled = !isTracked) {
                            onAddItem(asset.symbol, asset.name, asset.type, asset.defaultPrice, 0.0)
                        }
                        .border(
                            width = 1.dp,
                            color = if (isTracked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = asset.symbol,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTracked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (isTracked) Icons.Default.CheckCircle else if (asset.type == "STOCK") Icons.Default.TrendingUp else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isTracked) Color(0xFF10B981) else if (asset.type == "STOCK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = asset.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$${String.format("%,.2f", asset.defaultPrice)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isTracked) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioTabContent(
    portfolioItems: List<com.example.data.model.PortfolioItem>,
    marketQuotes: Map<String, com.example.data.network.MarketQuote> = emptyMap(),
    onDeleteItem: (com.example.data.model.PortfolioItem) -> Unit,
    onAddItem: (symbol: String, name: String, type: String, price: Double, qty: Double) -> Unit
) {
    if (portfolioItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = "Track Your Assets",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add stocks (e.g. AAPL, TSLA) or currency pairings (e.g. EUR/USD, USD/INR) to monitor values and holdings in one beautiful layout.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 320.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            QuickAddSuggestionsRow(
                portfolioItems = portfolioItems,
                onAddItem = onAddItem
            )
        }
    } else {
        // Calculate Portfolio Statistics
        var totalValue = 0.0
        var totalCost = 0.0
        portfolioItems.forEach { item ->
            val curPrice = marketQuotes[item.symbol.uppercase()]?.currentPrice ?: getMockCurrentPrice(item.symbol, item.purchasePrice)
            totalValue += curPrice * item.quantity
            totalCost += item.purchasePrice * item.quantity
        }
        val overallGainLoss = totalValue - totalCost
        val overallGainPercent = if (totalCost > 0) (overallGainLoss / totalCost) * 100.0 else 0.0

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Portfolio Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "PORTFOLIO VALUE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${String.format("%,.2f", totalValue)}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isProfit = overallGainLoss >= 0
                            Icon(
                                imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${if (isProfit) "+" else ""}$${String.format("%.2f", overallGainLoss)} (${if (isProfit) "+" else ""}${String.format("%.2f", overallGainPercent)}%) All Time",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Assets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${portfolioItems.size} Tracked", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Investment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format("%,.2f", totalCost)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Quick Add Row
            item {
                QuickAddSuggestionsRow(
                    portfolioItems = portfolioItems,
                    onAddItem = onAddItem
                )
            }

            // Asset Section Header
            item {
                Text(
                    text = "YOUR HOLDINGS & TRACKED ITEMS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
                )
            }

            // Portfolio Items List
            items(portfolioItems) { item ->
                PortfolioItemCard(
                    item = item,
                    currentPrice = marketQuotes[item.symbol.uppercase()]?.currentPrice ?: getMockCurrentPrice(item.symbol, item.purchasePrice),
                    onDelete = { onDeleteItem(item) }
                )
            }
        }
    }
}

@Composable
fun PortfolioItemCard(
    item: com.example.data.model.PortfolioItem,
    currentPrice: Double,
    onDelete: () -> Unit
) {
    val itemCost = item.purchasePrice * item.quantity
    val itemValue = currentPrice * item.quantity
    val gainLoss = itemValue - itemCost
    val isUp = currentPrice >= item.purchasePrice

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (item.type == "STOCK") {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.type == "STOCK") Icons.Default.ShowChart else Icons.Default.CurrencyExchange,
                    contentDescription = null,
                    tint = if (item.type == "STOCK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = item.symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.quantity > 0) {
                    Text(
                        text = "${String.format("%.4f", item.quantity)} owned",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Beautiful Canvas Sparkline
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .padding(horizontal = 8.dp)
            ) {
                SparklineGraph(symbol = item.symbol, isUp = isUp)
            }

            // Price & Gain/Loss
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1.3f)
            ) {
                Text(
                    text = "$${String.format("%,.2f", currentPrice)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (item.quantity > 0) {
                    Text(
                        text = "$${String.format("%,.2f", itemValue)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (gainLoss >= 0) "+" else ""}$${String.format("%.2f", gainLoss)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gainLoss >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                } else {
                    val percentChange = if (item.purchasePrice > 0) ((currentPrice - item.purchasePrice) / item.purchasePrice) * 100.0 else 0.0
                    Text(
                        text = "${if (percentChange >= 0) "+" else ""}${String.format("%.2f", percentChange)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (percentChange >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete item
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SparklineGraph(symbol: String, isUp: Boolean) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val points = mutableListOf<Float>()
        // Generate a pseudo-random deterministic curve based on the symbol string hash code
        val hash = symbol.hashCode()
        val randomGenerator = java.util.Random(hash.toLong())
        var current = 50f
        points.add(current)
        for (i in 1..8) {
            val delta = (randomGenerator.nextFloat() - 0.48f) * 25f // Slight upward bias if up, else random
            current += delta
            points.add(current)
        }

        // Standardize points to fit drawing height
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 100f
        val range = if (maxVal == minVal) 1f else maxVal - minVal

        val path = androidx.compose.ui.graphics.Path()
        val widthStep = size.width / (points.size - 1)
        
        points.forEachIndexed { index, value ->
            // Standardize to 10% to 90% of size.height
            val x = index * widthStep
            val y = size.height - (((value - minVal) / range) * (size.height * 0.8f) + (size.height * 0.1f))
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = if (isUp) Color(0xFF10B981) else Color(0xFFEF4444),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

fun getMockCurrentPrice(symbol: String, purchasePrice: Double): Double {
    val hash = Math.abs(symbol.hashCode())
    val random = java.util.Random(hash.toLong())
    // Return purchasePrice with a deterministic pseudo-random fluctuation between -12% and +18%
    val percentage = -0.12 + (random.nextDouble() * 0.30)
    return purchasePrice * (1.0 + percentage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPortfolioItemDialog(
    onDismiss: () -> Unit,
    onAddItem: (symbol: String, name: String, type: String, purchasePrice: Double, quantity: Double) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("STOCK") } // "STOCK" or "CURRENCY"
    var purchasePriceStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Track Custom Asset", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add details of the stock or currency asset to follow.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Asset Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedFilterChip(
                        selected = type == "STOCK",
                        onClick = { type = "STOCK" },
                        label = { Text("Stock/Equity") },
                        modifier = Modifier.weight(1f)
                    )
                    ElevatedFilterChip(
                        selected = type == "CURRENCY",
                        onClick = { type = "CURRENCY" },
                        label = { Text("Forex/Crypto") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Suggestion Chips
                Text(
                    text = "TAP TO AUTOFILL SUGGESTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val autofillSuggestions = listOf(
                    QuickAsset("AAPL", "Apple Inc.", "STOCK", 185.50),
                    QuickAsset("TSLA", "Tesla Inc.", "STOCK", 220.80),
                    QuickAsset("NVDA", "NVIDIA Corp.", "STOCK", 450.25),
                    QuickAsset("BTC", "Bitcoin", "CURRENCY", 65200.00),
                    QuickAsset("EUR/USD", "Euro / USD", "CURRENCY", 1.085),
                    QuickAsset("USD/INR", "USD / Rupee", "CURRENCY", 83.54)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(autofillSuggestions) { item ->
                        SuggestionChip(
                            onClick = {
                                symbol = item.symbol
                                name = item.name
                                type = item.type
                                purchasePriceStr = item.defaultPrice.toString()
                            },
                            label = { Text(item.symbol) },
                            icon = {
                                Icon(
                                    imageVector = if (item.type == "STOCK") Icons.Default.TrendingUp else Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("Ticker Symbol (e.g. AAPL, BTC)") },
                    singleLine = true,
                    placeholder = { Text("e.g. TSLA, EUR/USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Asset Name") },
                    singleLine = true,
                    placeholder = { Text("e.g. Tesla Motors, Euro / USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purchasePriceStr,
                    onValueChange = { purchasePriceStr = it },
                    label = { Text("Reference Price ($)") },
                    singleLine = true,
                    placeholder = { Text("e.g. 175.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Quantity (Optional)") },
                    singleLine = true,
                    placeholder = { Text("e.g. 10.0 (Leave 0 to just track price)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = purchasePriceStr.toDoubleOrNull() ?: 0.0
                    val qty = quantityStr.toDoubleOrNull() ?: 0.0
                    if (symbol.isNotBlank() && name.isNotBlank() && price > 0.0) {
                        onAddItem(symbol.trim().uppercase(), name.trim(), type, price, qty)
                    }
                },
                enabled = symbol.isNotBlank() && name.isNotBlank() && (purchasePriceStr.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Track Asset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private val FontWeight.Companion.BoldFamily: FontWeight get() = FontWeight.Bold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabContent(
    viewModel: NewsViewModel,
    isDarkModeActive: Boolean,
    customDarkMode: Boolean?,
    textSize: String,
    ttsSpeed: Float,
    ttsPitch: Float,
    selectedVoiceTone: String,
    savedArticlesCount: Int,
    customSourcesCount: Int,
    portfolioItemsCount: Int,
    appIconVariation: String = "Default"
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card or Welcome Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pulse Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Tailor your news and reading experience",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Appearance & Theme
        Text(
            text = "APPEARANCE & THEME",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode Preference",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (customDarkMode) {
                                true -> "Dark mode is forced on"
                                false -> "Light mode is forced on"
                                null -> "Currently following system default (${if (isDarkModeActive) "Dark" else "Light"})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Selector Buttons: System, Light, Dark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // System Default Chip
                    ElevatedFilterChip(
                        selected = customDarkMode == null,
                        onClick = { viewModel.setThemeMode(null) },
                        label = { Text("System") },
                        modifier = Modifier.weight(1f).testTag("theme_system_chip"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    // Light Chip
                    ElevatedFilterChip(
                        selected = customDarkMode == false,
                        onClick = { viewModel.setThemeMode(false) },
                        label = { Text("Light") },
                        modifier = Modifier.weight(1f).testTag("theme_light_chip"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    // Dark Chip
                    ElevatedFilterChip(
                        selected = customDarkMode == true,
                        onClick = { viewModel.setThemeMode(true) },
                        label = { Text("Dark") },
                        modifier = Modifier.weight(1f).testTag("theme_dark_chip"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Section: Intelligence Personalization
        Text(
            text = "INTELLIGENCE PERSONALIZATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        val currentRegion by viewModel.userRegion.collectAsState()
        val currentSector by viewModel.userSector.collectAsState()

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Designated Home Region",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Powers Local Impact Analysis and Regional Blindspot detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val regions = listOf("Global", "North America", "Europe", "South Asia", "East Asia", "Middle East", "Latin America", "Africa")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(regions) { r ->
                        FilterChip(
                            selected = currentRegion == r,
                            onClick = {
                                viewModel.setUserRegion(r)
                                Toast.makeText(context, "Designated Home Region set to $r", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(r, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Designated Sector Focus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tailors supply chain, energy, and market impact summaries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val sectors = listOf("Finance & Markets", "Technology", "Supply Chain & Logistics", "Energy & Commodities", "Everyday Consumer")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sectors) { s ->
                        FilterChip(
                            selected = currentSector == s,
                            onClick = { viewModel.setUserSector(s) },
                            label = { Text(s, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }


        // Section: App Launcher Icon
        Text(
            text = "APP LAUNCHER ICON",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Launcher Customization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Select a custom design for your home screen launcher icon. Changes are applied immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Vertical list of app icon options
                val options = listOf(
                    Triple("Default", "Factum Prism", "Glass prism refracting vibrant light spectrum"),
                    Triple("Gold3D", "Gold 3D Block", "Segmented isometric gold blocks 'F' logo"),
                    Triple("GoldOutline", "Gold Outline", "Geometric minimal gold line 'F' logo"),
                    Triple("Marble", "Marble Cube", "White marble cube with vertical silver slot")
                )

                options.forEach { (key, label, desc) ->
                    val isSelected = appIconVariation == key
                    val backgroundBrush = when (key) {
                        "Gold3D" -> Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                        "GoldOutline" -> Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                        "Marble" -> Brush.linearGradient(listOf(Color(0xFF000000), Color(0xFF121212)))
                        else -> Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                    }
                    val iconDrawableRes = when (key) {
                        "Gold3D" -> com.example.R.drawable.img_logo_factum_gold_3d_1784449120846
                        "GoldOutline" -> com.example.R.drawable.img_logo_factum_gold_outline_1784449131846
                        "Marble" -> com.example.R.drawable.img_logo_factum_marble_1784449141719
                        else -> com.example.R.drawable.img_logo_factum_prism_1784449153942
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppIconVariation(key)
                                Toast.makeText(context, "App icon updated to $label!", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("app_icon_${key.lowercase()}_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            }
                        ),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Icon Mockup Preview
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(backgroundBrush)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = iconDrawableRes,
                                    contentDescription = "$label Preview",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Info details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Selection Indicator
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Typography & Text Size
        Text(
            text = "TYPOGRAPHY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Article Body Text Size",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Adjust the reading scale of full reports and AI summaries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizes = listOf("Small", "Medium", "Large")
                    sizes.forEach { size ->
                        val isSelected = textSize == size
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTextSize(size) },
                            label = { Text(size) },
                            modifier = Modifier.weight(1f).testTag("text_size_${size.lowercase()}_chip")
                        )
                    }
                }

                // Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Preview: Pulse delivers direct, curated, and beautiful live updates with precision.",
                        fontSize = when (textSize) {
                            "Small" -> 13.sp
                            "Large" -> 18.sp
                            else -> 15.sp
                        },
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Section: Text-To-Speech (TTS) Voice Controls
        Text(
            text = "SPEECH & READER VOICES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preset Voice Profile
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Voice Tone Preset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sets human-sounding cadence presets for AI-summaries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tones = listOf("Warm", "Crisp", "Natural")
                        tones.forEach { tone ->
                            val isSelected = selectedVoiceTone == tone
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setVoiceTone(tone) },
                                label = { Text(tone) },
                                modifier = Modifier.weight(1f).testTag("voice_tone_${tone.lowercase()}_chip")
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Fine controls: Speed
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech Rate (Speed)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${String.format("%.2f", ttsSpeed)}x",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { viewModel.setTtsSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("tts_speed_slider")
                    )
                }

                // Fine controls: Pitch
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech Pitch",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${String.format("%.2f", ttsPitch)}x",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = ttsPitch,
                        onValueChange = { viewModel.setTtsPitch(it) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("tts_pitch_slider")
                    )
                }
            }
        }

        // Section: System Statistics
        Text(
            text = "FIREBASE CLOUD MESSAGING & PUSH ALERTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        val fcmToken by viewModel.fcmToken.collectAsState()
        val userRegion by viewModel.userRegion.collectAsState()
        val userSector by viewModel.userSector.collectAsState()

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FCM Breaking News Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Receive real-time push alerts when breaking news is detected in $userRegion for $userSector.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SuggestionChip(
                        onClick = { },
                        label = { Text("FCM Active") },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // FCM Topics Subscribed
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Subscribed Topics",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text("topic: breaking_news") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("topic: region_${userRegion.replace(" ", "_").lowercase()}") }
                        )
                    }
                }

                // FCM Registration Token Display
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Device Registration Token",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = fcmToken ?: "Connecting to Firebase Cloud Messaging...",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Test FCM Push Notification Action
                Button(
                    onClick = {
                        viewModel.simulatePersonalizedPushNotification(context)
                        Toast.makeText(context, "⚡ Simulated FCM Push Notification sent!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_fcm_push_button")
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ Test FCM Push Notification Now", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section: System Statistics
        Text(
            text = "STATISTICS & DATA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Database Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Saved Count
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = savedArticlesCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Saved Stories",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Custom Sources Count
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = customSourcesCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Custom Sources",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Portfolio Assets Count
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = portfolioItemsCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Assets Tracked",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Section: About App
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Pulse News & Portfolio Tracker",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Version 1.0.0 (AI Studio Production)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Equipped with Gemini Real-Time Synthesis Engines",
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// FACTUM INTELLIGENCE MODULES COMPOSABLES
// ==========================================

@Composable
fun BlindspotAlertCard(
    report: com.example.data.model.BlindspotReport?,
    isLoading: Boolean,
    userRegion: String = "Global",
    onRescan: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("blindspot_alert_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "BLINDSPOT ALERT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    if (report != null) {
                        Text(
                            text = report.trendingRegion,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Home: $userRegion",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRescan,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan Blindspots",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Scanning cross-outlet coverage patterns across global media...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (report != null) {
                Text(
                    text = report.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = report.alertText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = "WHAT YOU'RE MISSING:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = report.missingContext,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Top Source: ${report.topSource}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Footprint in Western Feeds: Low / Near-Zero",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap refresh to scan global media clusters for overlooked regional stories.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CatchMeUpSection(
    article: NewsArticle,
    timelineText: String?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .testTag("catch_me_up_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "CATCH ME UP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "6-12 Months Backstory",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (timelineText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = timelineText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Strictly factual • Chronologically ordered • <60 words",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Extracting 6-12 month historical backstory...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Backstory Context", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InteractiveArticleChatSection(
    article: NewsArticle,
    messages: List<com.example.data.model.ArticleChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("interactive_article_chat_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "INTERACTIVE ARTICLE CHAT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Answers strictly using provided article text",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    IconButton(onClick = onClearChat, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Question Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val suggestions = listOf("Who is involved?", "What caused this?", "When did this occur?", "What are the core facts?")
                items(suggestions) { sugg ->
                    AssistChip(
                        onClick = { onSendMessage(sugg) },
                        label = { Text(sugg, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Messages Container
            if (messages.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    messages.forEach { msg ->
                        val isUser = msg.sender == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 2.dp,
                                    bottomEnd = if (isUser) 2.dp else 12.dp
                                ),
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.message,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(10.dp),
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Consulting article text as sole source of truth...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Input field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask anything about this article...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .background(
                            color = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Question",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RealtimeNuancedTranslationSection(
    article: NewsArticle,
    translatedText: String?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("nuanced_translation_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "REALTIME NUANCED TRANSLATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "Preserves Terms & Regional Context",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (translatedText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = translatedText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Inline [Note: ...] brackets provide instant cultural & political context.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Translating with regional terminology & context brackets...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Translate to Nuanced English", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LocalImpactSection(
    article: NewsArticle,
    userRegion: String,
    userSector: String,
    impactText: String?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .testTag("local_impact_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "WHY THIS MATTERS TO YOU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(userRegion, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                    SuggestionChip(
                        onClick = { },
                        label = { Text(userSector, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (impactText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = impactText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Synthesizing local impact for $userRegion & $userSector...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze Local Consequences", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

