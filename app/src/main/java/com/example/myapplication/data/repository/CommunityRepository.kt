package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.remote.CommunityPick
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepository @Inject constructor() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val picksCollection by lazy { firestore.collection("community_picks") }

    fun getTopCommunityPick(): Flow<CommunityPick?> = callbackFlow {
        val subscription = picksCollection
            .orderBy("upvotes", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CommunityRepo", "Listen failed: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                val pick = snapshot?.documents?.firstOrNull()?.let { doc ->
                    try {
                        doc.toObject(CommunityPick::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("CommunityRepo", "Mapping error: ${e.message}")
                        null
                    }
                }
                trySend(pick)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun upvoteContent(content: ContentSourceEntity) {
        val docId = content.url.hashCode().toString().replace("-", "n")
        val docRef = picksCollection.document(docId)

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                
                if (!snapshot.exists()) {
                    val newPick = hashMapOf(
                        "id" to docId,
                        "title" to content.title,
                        "summary" to content.summary,
                        "url" to content.url,
                        "imageUrl" to content.imageUrl,
                        "upvotes" to 1L,
                        "date" to System.currentTimeMillis()
                    )
                    transaction.set(docRef, newPick)
                } else {
                    val currentUpvotes = snapshot.getLong("upvotes") ?: 0L
                    transaction.update(docRef, "upvotes", currentUpvotes + 1L)
                    transaction.update(docRef, "date", System.currentTimeMillis())
                    // Optionally update imageUrl if it was missing
                    if (snapshot.getString("imageUrl") == null && content.imageUrl != null) {
                        transaction.update(docRef, "imageUrl", content.imageUrl)
                    }
                }
            }.await()
            Log.d("CommunityRepo", "Successfully upvoted: $docId")
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Transaction failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun upvoteExistingPick(pickId: String) {
        val docRef = picksCollection.document(pickId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentUpvotes = snapshot.getLong("upvotes") ?: 0L
                transaction.update(docRef, "upvotes", currentUpvotes + 1L)
            }.await()
        } catch (e: Exception) {
            Log.e("CommunityRepo", "Upvote existing failed: ${e.message}")
            throw e
        }
    }
}
