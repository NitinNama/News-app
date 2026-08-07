package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_sources")
data class NewsSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val category: String,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false
)

@Entity(tableName = "saved_articles")
data class SavedArticle(
    @PrimaryKey val link: String, // Link is unique and works perfectly as a primary key
    val title: String,
    val description: String,
    val pubDate: String,
    val sourceName: String,
    val category: String,
    val imageUrl: String?,
    val savedAt: Long = System.currentTimeMillis()
)

data class NewsArticle(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val sourceName: String,
    val category: String,
    val imageUrl: String? = null,
    val isBookmarked: Boolean = false
)

@Entity(tableName = "cached_articles")
data class CachedArticle(
    @PrimaryKey val link: String,
    val title: String,
    val description: String,
    val pubDate: String,
    val sourceName: String,
    val category: String,
    val imageUrl: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_items")
data class PortfolioItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val name: String,
    val type: String, // "STOCK" or "CURRENCY"
    val purchasePrice: Double,
    val quantity: Double,
    val addedAt: Long = System.currentTimeMillis()
)

data class BlindspotReport(
    val headline: String,
    val alertText: String,
    val trendingRegion: String,
    val overlookedRegion: String,
    val missingContext: String,
    val topSource: String
)

data class ArticleChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)


