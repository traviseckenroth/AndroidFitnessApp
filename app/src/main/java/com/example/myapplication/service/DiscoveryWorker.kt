package com.example.myapplication.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.repository.ContentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.URLEncoder

@HiltWorker
class DiscoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contentRepository: ContentRepository,
    private val workoutDao: WorkoutDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tagNames = inputData.getStringArray("tagNames") ?: return Result.failure()
        
        return try {
            val batchContent = mutableListOf<ContentSourceEntity>()
            tagNames.forEach { tagName ->
                try {
                    contentRepository.fetchRealContent(tagName)

                    val cleanTag = tagName.replace(" ", "").lowercase()
                    val encodedTag = URLEncoder.encode(tagName, "UTF-8")

                    batchContent.add(ContentSourceEntity(
                        title = "Instagram: #$tagName",
                        summary = "Latest trending posts and training reels for $tagName.",
                        url = "https://www.instagram.com/explore/tags/$cleanTag/",
                        imageUrl = "https://www.instagram.com/static/images/ico/favicon-192.png/68d99ad29166.png",
                        mediaType = "Social",
                        sportTag = tagName,
                        dateFetched = System.currentTimeMillis()
                    ))

                    val redditSub = if (tagName.contains("Hyrox", true)) "hyrox" else if (tagName.contains("CrossFit", true)) "crossfit" else "fitness"
                    batchContent.add(ContentSourceEntity(
                        title = "Reddit: r/$redditSub",
                        summary = "Join the community discussion and see what's trending in $tagName.",
                        url = "https://www.reddit.com/r/$redditSub/new/",
                        imageUrl = "https://www.redditstatic.com/desktop2x/img/favicon/android-icon-192x192.png",
                        mediaType = "Social",
                        sportTag = tagName,
                        dateFetched = System.currentTimeMillis()
                    ))

                    batchContent.add(ContentSourceEntity(
                        title = "YouTube: $tagName Training",
                        summary = "Watch recent training footage and competition highlights for $tagName.",
                        url = "https://www.youtube.com/results?search_query=$encodedTag+training+highlights",
                        imageUrl = "https://www.youtube.com/s/desktop/28e5c60b/img/favicon_144x144.png",
                        mediaType = "Video",
                        sportTag = tagName,
                        dateFetched = System.currentTimeMillis()
                    ))

                    batchContent.add(ContentSourceEntity(
                        title = "X: #$tagName Updates",
                        summary = "Real-time news and athlete updates for $tagName.",
                        url = "https://twitter.com/hashtag/$cleanTag",
                        imageUrl = "https://abs.twimg.com/favicons/twitter.2.ico",
                        mediaType = "Social",
                        sportTag = tagName,
                        dateFetched = System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    Log.e("DiscoveryWorker", "Error creating content for $tagName: ${e.message}")
                }
            }
            if (batchContent.isNotEmpty()) {
                workoutDao.insertContentSources(batchContent)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("DiscoveryWorker", "Worker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
