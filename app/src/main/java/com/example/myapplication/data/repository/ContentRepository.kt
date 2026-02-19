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
            // If data is fresh (less than 4 hours old) AND we have thumbnails, use it
            if (System.currentTimeMillis() - mostRecent < 4 * 60 * 60 * 1000) {
                if (existingContent.all { it.imageUrl != null }) {
                    return@withContext existingContent
                }
            }
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val rssUrl = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en&scoring=n"
            val doc = Jsoup.connect(rssUrl)
                .timeout(10000)
                .parser(Parser.xmlParser())
                .get()

            val items = doc.select("item")
            val savedList = mutableListOf<ContentSourceEntity>()

            items.take(15).forEach { item ->
                val title = item.select("title").text()
                val url = item.select("link").text()
                
                // Enhanced image extraction logic
                val description = item.select("description").text()
                var imageUrl = extractImageUrl(description)
                
                if (imageUrl == null) {
                    imageUrl = item.select("media|content").attr("url").takeIf { it.isNotBlank() }
                }
                if (imageUrl == null) {
                    imageUrl = item.select("media|thumbnail").attr("url").takeIf { it.isNotBlank() }
                }
                if (imageUrl == null) {
                    imageUrl = item.select("enclosure[type^=image]").attr("url").takeIf { it.isNotBlank() }
                }

                // YouTube predictably has thumbnails
                if (imageUrl == null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    val videoId = extractVideoId(url)
                    if (videoId != null) {
                        imageUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                    }
                }

                val entity = ContentSourceEntity(
                    title = title,
                    summary = item.select("pubDate").text(),
                    url = url,
                    imageUrl = imageUrl,
                    mediaType = if (url.contains("youtube.com") || url.contains("youtu.be")) "Video" else "Article",
                    sportTag = query,
                    dateFetched = System.currentTimeMillis()
                )
                
                try {
                    workoutDao.insertContentSource(entity)
                    savedList.add(entity)
                } catch (e: Exception) {
                    savedList.add(entity)
                }
            }
            return@withContext savedList

        } catch (e: Exception) {
            Log.e("ContentRepo", "Crawling failed for $query", e)
            return@withContext existingContent ?: emptyList()
        }
    }

    private fun extractImageUrl(html: String): String? {
        if (html.isBlank()) return null
        return try {
            val doc = Jsoup.parse(html)
            val img = doc.select("img").first()
            var src = img?.attr("src")
            
            if (src != null && src.startsWith("//")) {
                src = "https:$src"
            }
            src
        } catch (e: Exception) {
            null
        }
    }

    private fun extractVideoId(url: String): String? {
        return try {
            if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            } else if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore("&")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getContentById(id: Long): Flow<ContentSourceEntity?> {
        return workoutDao.getContentSourceById(id)
    }
}