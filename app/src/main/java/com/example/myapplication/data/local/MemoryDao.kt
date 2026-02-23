package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Query("SELECT * FROM user_memories WHERE exerciseName = :exerciseName")
    suspend fun getMemoriesForExercise(exerciseName: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<UserMemoryEntity>
}