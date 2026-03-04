package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // FIX: Added missing Flow import

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Query("SELECT * FROM user_memories WHERE exerciseName = :exerciseName")
    suspend fun getMemoriesForExercise(exerciseName: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<UserMemoryEntity>

    @Query("DELETE FROM user_memories")
    suspend fun deleteAllMemories(): Int

    @Query("SELECT * FROM user_memories WHERE category = 'Pain' ORDER BY timestamp DESC")
    fun getLimitationsFlow(): Flow<List<UserMemoryEntity>>

    // FIX: Removed the duplicate version of this function
    @Delete
    suspend fun deleteMemory(memory: UserMemoryEntity): Int
}