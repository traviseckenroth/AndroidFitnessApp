// File: app/src/main/java/com/example/myapplication/data/repository/ContentRepository.kt
package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class ContentRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    // Cache for Daily Briefing to prevent unnecessary refreshes
    private var cachedBriefing: String = ""
    private var cachedInterestTags: Set<String> = emptySet()
    private var cachedWorkoutTitle: String? = null

    fun getCachedBriefing(interests: List<String>, workoutTitle: String?): String? {
        val interestSet = interests.toSet()
        // We only return the cache if interests haven't changed AND workout title is same
        if (cachedBriefing.isNotEmpty() && cachedInterestTags == interestSet && cachedWorkoutTitle == workoutTitle) {
            return cachedBriefing
        }
        return null
    }

    fun updateBriefingCache(briefing: String, interests: List<String>, workoutTitle: String?) {
        cachedBriefing = briefing
        cachedInterestTags = interests.toSet()
        cachedWorkoutTitle = workoutTitle
    }

    suspend fun fetchRealContent(query: String): List<ContentSourceEntity> = withContext(Dispatchers.IO) {
        // Local-First: Check if we already have recent content for this tag in the DB
        val existingContent = workoutDao.getSubscribedContent().firstOrNull()?.filter { it.sportTag == query }
        if (existingContent != null && existingContent.isNotEmpty()) {
            val mostRecent = existingContent.maxOf { it.dateFetched }
            // If data is less than 6 hours old, return cached Room data
            if (System.currentTimeMillis() - mostRecent < 6 * 60 * 60 * 1000) {
                return@withContext existingContent
            }
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val rssUrl = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en&scoring=n"
            val doc = Jsoup.connect(rssUrl)
                .timeout(8000)
                .parser(Parser.xmlParser())
                .get()

            val items = doc.select("item")
            val savedList = mutableListOf<ContentSourceEntity>()

            items.take(10).forEach { item ->
                val title = item.select("title").text()
                val url = item.select("link").text()
                
                // Try to extract a thumbnail/image if available in description or media:content
                val description = item.select("description").text()
                val imageUrl = extractImageUrl(description) ?: item.select("media|content").attr("url").takeIf { it.isNotBlank() }

                val entity = ContentSourceEntity(
                    title = title,
                    summary = item.select("pubDate").text(),
                    url = url,
                    imageUrl = imageUrl,
                    mediaType = if (url.contains("youtube.com") || url.contains("youtu.be")) "Video" else "Article",
                    sportTag = query,
                    dateFetched = System.currentTimeMillis()
                )
                // Save to DB (Room handles uniqueness via URL index)
                try {
                    val newId = workoutDao.insertContentSource(entity)
                    savedList.add(entity.copy(sourceId = newId))
                } catch (e: Exception) {
                    // Item likely already exists, fetch it if needed or just skip
                }
            }
            return@withContext savedList

        } catch (e: Exception) {
            Log.e("ContentRepo", "Crawling failed", e)
            return@withContext existingContent ?: emptyList()
        }
    }

    private fun extractImageUrl(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            val img = doc.select("img").first()
            img?.attr("src")
        } catch (e: Exception) {
            null
        }
    }

    fun getContentById(id: Long): Flow<ContentSourceEntity?> {
        return workoutDao.getContentSourceById(id)
    }
}