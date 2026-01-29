package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        DailyWorkoutEntity::class,
        WorkoutSetEntity::class,
        CompletedWorkoutEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    private class Callback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.workoutDao())
                }
            }
        }

        // Note: This wipes the DB on version change (e.g. v1 -> v2)
        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.workoutDao())
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workout_db_v15"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(Callback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- POPULATION LOGIC (Updated to use ExerciseEntity) ---
suspend fun populateDatabase(dao: WorkoutDao) {
    dao.deleteAllExercises()

    suspend fun ex(name: String, group: String, equip: String, tierString: String, load: String, fatigue: String, note: String) {
        // Parse "T1", "T2" string to Int for the new 'tier' field
        val tierInt = when {
            tierString.contains("1") -> 1
            tierString.contains("2") -> 2
            else -> 3
        }

        dao.insertExercise(
            ExerciseEntity(
                name = name,
                muscleGroup = group,
                equipment = equip,
                tier = tierInt,
                loadability = load,
                fatigue = fatigue,
                notes = note
            )
        )
    }

    // ==================== CHEST ====================
    ex("Barbell Bench Press", "Chest", "Barbell", "T1", "High", "High", "Primary strength builder")
    ex("Dumbbell Bench Press", "Chest", "Dumbbell", "T1", "Med", "Med", "Good for symmetry")
    ex("Incline Barbell Press", "Chest", "Barbell", "T1", "High", "High", "Upper chest mass builder")
    ex("Incline Dumbbell Press", "Chest", "Dumbbell", "T2", "Med", "Med", "Deep stretch for upper chest")
    ex("Machine Chest Press", "Chest", "Machine", "T2", "High", "Low", "Safe secondary compound")
    ex("Dumbbell Fly", "Chest", "Dumbbell", "T3", "Low", "Low", "Focus on stretch, do not go heavy")
    ex("Cable Fly", "Chest", "Cable", "T3", "Low", "Low", "Constant tension")
    ex("Pec Deck", "Chest", "Machine", "T3", "Med", "Low", "Safest fly variation")
    ex("Dips (Weighted)", "Chest", "Bodyweight", "T1", "High", "High", "Lower chest mass builder")
    ex("Push-Up", "Chest", "Bodyweight", "T3", "Low", "Low", "Finisher or warmup")

    // ==================== SHOULDERS ====================
    ex("Overhead Press (Standing)", "Shoulders", "Barbell", "T1", "High", "High", "Primary overhead strength")
    ex("Seated Dumbbell Press", "Shoulders", "Dumbbell", "T1", "Med", "Med", "Standard hypertrophy builder")
    ex("Arnold Press", "Shoulders", "Dumbbell", "T2", "Med", "Med", "Long time under tension")
    ex("Machine Shoulder Press", "Shoulders", "Machine", "T2", "High", "Low", "Good after free weights")
    ex("Cable Lateral Raise", "Shoulders", "Cable", "T3", "Low", "Low", "Best resistance profile")
    ex("Face Pull", "Shoulders", "Cable", "T3", "Med", "Low", "Essential for posture/health")
    ex("Rear Delt Fly (Dumbbell)", "Shoulders", "Dumbbell", "T3", "Low", "Low", "Hard to master")
    ex("Reverse Pec Deck", "Shoulders", "Machine", "T3", "Med", "Low", "Easy rear delt isolation")
    ex("Upright Row", "Shoulders", "Barbell", "T2", "Med", "Med", "Watch wrist health")

    // ==================== BACK ====================
    ex("Deadlift", "Back", "Barbell", "T1", "High", "High", "Systemic fatigue is very high")
    ex("Pull-Up", "Back", "Bodyweight", "T1", "High", "High", "Squat of the upper body")
    ex("Lat Pulldown", "Back", "Cable", "T2", "Med", "Low", "Volume builder")
    ex("Barbell Row", "Back", "Barbell", "T1", "High", "High", "Thickness builder")
    ex("Dumbbell Row", "Back", "Dumbbell", "T2", "Med", "Med", "Great ROM")
    ex("Seated Cable Row", "Back", "Cable", "T2", "Med", "Low", "Mid-back builder")
    ex("T-Bar Row", "Back", "Machine", "T1", "High", "Med", "Heavy loading")
    ex("Chest Supported Row", "Back", "Machine", "T2", "Med", "Low", "Saves lower back")
    ex("Straight Arm Pulldown", "Back", "Cable", "T3", "Low", "Low", "Lat isolation")
    ex("Shrugs", "Back", "Dumbbell", "T3", "High", "Low", "Trap isolation")

    // ==================== LEGS ====================
    ex("Squat (High Bar)", "Legs", "Barbell", "T1", "High", "High", "King of legs")
    ex("Front Squat", "Legs", "Barbell", "T1", "High", "High", "Biases quads")
    ex("Leg Press", "Legs", "Machine", "T2", "High", "Med", "Volume builder")
    ex("Hack Squat", "Legs", "Machine", "T1", "High", "Med", "Great hypertrophy")
    ex("Bulgarian Split Squat", "Legs", "Dumbbell", "T2", "Med", "High", "Hardest accessory")
    ex("Romanian Deadlift", "Legs", "Barbell", "T1", "High", "Med", "Hamstring king")
    ex("Leg Extension", "Legs", "Machine", "T3", "Med", "Low", "Quad isolation")
    ex("Leg Curl (Seated)", "Legs", "Machine", "T3", "Med", "Low", "Hamstring isolation")
    ex("Calf Raise (Standing)", "Legs", "Machine", "T3", "Med", "Low", "Gastrocnemius focus")
    ex("Hip Thrust", "Legs", "Barbell", "T1", "High", "Med", "Glute builder")

    // ==================== ARMS ====================
    ex("Barbell Curl", "Arms", "Barbell", "T2", "High", "Low", "Heavy loader")
    ex("Dumbbell Curl", "Arms", "Dumbbell", "T3", "Med", "Low", "Standard curl")
    ex("Hammer Curl", "Arms", "Dumbbell", "T3", "High", "Low", "Forearm/Brachialis")
    ex("Preacher Curl", "Arms", "Machine", "T3", "Med", "Low", "Short head focus")
    ex("Close Grip Bench", "Arms", "Barbell", "T1", "High", "Med", "Compound triceps")
    ex("Skullcrusher", "Arms", "EZ_Bar", "T2", "Med", "Low", "Long head focus")
    ex("Cable Pushdown (Rope)", "Arms", "Cable", "T3", "Med", "Low", "Squeeze focus")
    ex("Overhead Extension", "Arms", "Dumbbell", "T3", "Med", "Low", "Stretch focus")

    // ==================== OLYMPIC LIFTING ====================
    ex("Barbell Snatch", "Full Body", "Barbell", "T1", "High", "High", "Highest technical demand. Do first.")
    ex("Clean and Jerk", "Full Body", "Barbell", "T1", "High", "High", "Primary strength-speed metric.")
    ex("Power Clean", "Full Body", "Barbell", "T1", "High", "Med", "Athletic power development.")
    ex("Hang Clean", "Full Body", "Barbell", "T2", "High", "Med", "Focus on second pull.")
    ex("Overhead Squat", "Legs", "Barbell", "T2", "Med", "Med", "Ultimate core stability.")
    ex("Push Press", "Shoulders", "Barbell", "T2", "High", "Med", "Overload for shoulders.")

    // ==================== CROSSFIT / FUNCTIONAL ====================
    ex("Thruster", "Full Body", "Barbell", "T1", "Med", "High", "Metabolically demanding.")
    ex("Handstand Push-Up", "Shoulders", "Bodyweight", "T1", "Med", "Med", "High skill gymnastics.")
    ex("Box Jump", "Legs", "Bodyweight", "T2", "Low", "Med", "Explosive power.")
    ex("Toes-to-Bar", "Core", "Bodyweight", "T2", "Low", "Med", "Gymnastic core work.")
    ex("Double Under", "Cardio", "Bodyweight", "T3", "Low", "High", "Conditioning.")

    // ==================== POWERLIFTING SPECIFIC ====================
    ex("Pause Squat", "Legs", "Barbell", "T2", "High", "High", "Power out of the hole.")
    ex("Pin Squat", "Legs", "Barbell", "T2", "High", "High", "Overload sticking points.")
    ex("Spoto Press", "Chest", "Barbell", "T2", "High", "Med", "Pause off chest.")
    ex("Deficit Deadlift", "Back", "Barbell", "T2", "High", "High", "Increased ROM.")
    ex("Board Press", "Arms", "Barbell", "T2", "High", "Med", "Overload lockout.")
}