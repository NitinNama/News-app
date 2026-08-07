package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NewsSource
import com.example.data.model.SavedArticle
import com.example.data.model.PortfolioItem
import com.example.data.model.CachedArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    // News Sources Queries
    @Query("SELECT * FROM news_sources ORDER BY isCustom ASC, name ASC")
    fun getAllSources(): Flow<List<NewsSource>>

    @Query("SELECT * FROM news_sources WHERE isEnabled = 1")
    fun getEnabledSources(): Flow<List<NewsSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: NewsSource)

    @Update
    suspend fun updateSource(source: NewsSource)

    @Delete
    suspend fun deleteSource(source: NewsSource)

    @Query("SELECT COUNT(*) FROM news_sources")
    suspend fun getSourceCount(): Int

    // Saved Articles Queries
    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC")
    fun getSavedArticles(): Flow<List<SavedArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedArticle(article: SavedArticle)

    @Query("DELETE FROM saved_articles WHERE link = :link")
    suspend fun deleteSavedArticleByLink(link: String)

    @Query("SELECT COUNT(*) FROM saved_articles WHERE link = :link")
    suspend fun isArticleSaved(link: String): Int

    // Portfolio Queries
    @Query("SELECT * FROM portfolio_items ORDER BY addedAt DESC")
    fun getAllPortfolioItems(): Flow<List<PortfolioItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioItem(item: PortfolioItem)

    @Query("DELETE FROM portfolio_items WHERE id = :id")
    suspend fun deletePortfolioItem(id: Int)

    // Cached Articles (Offline Cache) Queries
    @Query("SELECT * FROM cached_articles ORDER BY cachedAt DESC LIMIT 200")
    suspend fun getAllCachedArticles(): List<CachedArticle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedArticles(articles: List<CachedArticle>)

    @Query("DELETE FROM cached_articles")
    suspend fun clearCachedArticles()
}
