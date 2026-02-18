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

@Singleton
class ContentRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    suspend fun fetchRealContent(query: String): List<ContentSourceEntity> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val rssUrl = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en"

            Log.d("ContentRepo", "Fetching URL: $rssUrl")

            val doc = Jsoup.connect(rssUrl)
                .timeout(5000)
                .parser(Parser.xmlParser())
                .get()

            val items = doc.select("item")
            val savedList = mutableListOf<ContentSourceEntity>()

            // Process top 5 items
            items.take(5).forEach { item ->
                val entity = ContentSourceEntity(
                    title = item.select("title").text(),
                    summary = item.select("pubDate").text(),
                    url = item.select("link").text(),
                    mediaType = "Article",
                    sportTag = query,
                    dateFetched = System.currentTimeMillis()
                )

                // FIX: Save to DB immediately to generate a real ID
                val newId = workoutDao.insertContentSource(entity)
                savedList.add(entity.copy(sourceId = newId))
            }

            Log.d("ContentRepo", "Saved ${savedList.size} items for query: $query")
            return@withContext savedList

        } catch (e: Exception) {
            Log.e("ContentRepo", "Crawling failed for $query", e)
            return@withContext emptyList()
        }
    }
}