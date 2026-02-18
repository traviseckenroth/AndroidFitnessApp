// File: app/src/main/java/com/example/myapplication/data/local/DiscoveryEntities.kt
package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores crawled content like articles and videos from sports sources (e.g., Hyrox)
 * or specific athletes.
 */
@Entity(tableName = "content_sources")
data class ContentSourceEntity(
    @PrimaryKey(autoGenerate = true) val sourceId: Long = 0,
    val title: String,
    val summary: String,
    val url: String,
    val mediaType: String, // "Video" or "Article"
    val sportTag: String,  // e.g., "Hyrox"
    val athleteTag: String? = null, // e.g., "Hunter McIntyre"
    val dateFetched: Long = System.currentTimeMillis()
)

/**
 * Stores the sports or athletes the user has chosen to follow.
 */
@Entity(tableName = "user_subscriptions")
data class UserSubscriptionEntity(
    @PrimaryKey val tagName: String, // e.g., "Hyrox" or "Hunter McIntyre"
    val type: String // "Sport" or "Athlete"
)