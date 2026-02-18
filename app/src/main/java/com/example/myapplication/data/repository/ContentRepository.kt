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
        try {
            // FIX: Encode URL parameters
            val isAthleteSearch = query.contains("athlete", true) || !query.contains("fitness", true)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val rssUrl = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en&scoring=n"
            val doc = Jsoup.connect(rssUrl)
                .timeout(8000)
                .parser(Parser.xmlParser())
                .get()

            val items = doc.select("item")
            val savedList = mutableListOf<ContentSourceEntity>()

            items.take(5).forEach { item ->
                val entity = ContentSourceEntity(
                    title = item.select("title").text(),
                    summary = item.select("pubDate").text(),
                    url = item.select("link").text(),
                    mediaType = "Article",
                    sportTag = query,
                    dateFetched = System.currentTimeMillis()
                )
                // Save to DB to generate IDs
                val newId = workoutDao.insertContentSource(entity)
                savedList.add(entity.copy(sourceId = newId))
            }
            return@withContext savedList

        } catch (e: Exception) {
            Log.e("ContentRepo", "Crawling failed", e)
            return@withContext emptyList()
        }
    }

    // FIX: Moved this OUTSIDE of fetchRealContent
    fun getContentById(id: Long): Flow<ContentSourceEntity?> {
        return workoutDao.getContentSourceById(id)
    }
}