package com.example.myapplication.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val exerciseId: Long = 0,
    val name: String,
    val muscleGroup: String?, // "Legs", "Back"
    val majorMuscle: String,  // "Quads", "Lats" (NEW - For Swaps)
    val minorMuscle: String? = null, // "Glutes", "Biceps" (NEW)
    val equipment: String?,
    val tier: Int,
    val loadability: String?,
    val fatigue: String?,
    val notes: String,
    val description: String,
    val estimatedTimePerSet: Double,
    val videoUrl: String? = null
)

// ... WorkoutPlanEntity, DailyWorkoutEntity, etc. (No changes needed) ...
@Entity(tableName = "plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val planId: Long = 0,
    val name: String,
    val startDate: Long,
    val goal: String,
    val programType: String,
    val nutritionJson: String? = null
)

@Entity(
    tableName = "daily_workouts",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["planId"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"])]
)
data class DailyWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val workoutId: Long = 0,
    val planId: Long,
    val scheduledDate: Long,
    val title: String,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = DailyWorkoutEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"]
        )
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["exerciseId"])]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val suggestedReps: Int,
    val suggestedRpe: Int,
    val suggestedLbs: Int,
    val actualReps: Int? = null,
    val actualRpe: Float? = null,
    val actualLbs: Float? = null,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "completed_workouts",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"]
        )
    ],
    indices = [Index(value = ["exerciseId"])]
)
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: Long,
    val reps: Int,
    val rpe: Int,
    val weight: Int
) {
    val totalReps: Int
        get() = reps
    val totalVolume: Int
        get() = reps * weight
}

data class CompletedWorkoutWithExercise(
    @Embedded
    val completedWorkout: CompletedWorkoutEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "exerciseId"
    )
    val exercise: ExerciseEntity
)