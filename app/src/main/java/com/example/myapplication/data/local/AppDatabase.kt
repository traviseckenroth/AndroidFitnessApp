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
    version = 3,
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
                    "workout_db_v16" // Increment this string to force a wipe/re-populate if needed
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

// --- POPULATION LOGIC (Updated with Auto-Time Calculation) ---
suspend fun populateDatabase(dao: WorkoutDao) {
    dao.deleteAllExercises()

    suspend fun ex(name: String, group: String, equip: String, tierString: String, load: String, fatigue: String, note: String) {
        // 1. Parse "T1", "T2" string to Int
        val tierInt = when {
            tierString.contains("1") -> 1
            tierString.contains("2") -> 2
            else -> 3
        }

        // 2. AUTO-CALCULATE TIME BASED ON TIER (The logic we discussed)
        val timeVal = when (tierString) {
            "T1" -> 3.0  // Heavy Compound (includes warmup/rest)
            "T2" -> 2.5  // Accessory
            "T3" -> 2.0  // Isolation
            else -> 2.5
        }

        dao.insertExercise(
            ExerciseEntity(
                name = name,
                muscleGroup = group,
                equipment = equip,
                tier = tierInt,
                loadability = load,
                fatigue = fatigue,
                notes = note,
                estimatedTimePerSet = timeVal // This saves the calculated time to the DB
            )
        )
    }

    // ==================== CHEST ====================
    ex("Barbell Bench Press", "Chest", "Barbell", "T1", "High", "High", "Primary horizontal push for max loading. Targets sternal pecs and triceps.")
    ex("Dumbbell Bench Press", "Chest", "Dumbbell", "T1", "Med", "Med", "Increases range of motion over barbell. excellent for fixing asymmetry.")
    ex("Incline Barbell Press", "Chest", "Barbell", "T1", "High", "High", "Biases clavicular head (upper chest). Essential for full shelf development.")
    ex("Incline Dumbbell Press", "Chest", "Dumbbell", "T2", "Med", "Med", "Best movement for upper chest hypertrophy due to deep stretch at bottom.")
    ex("Machine Chest Press", "Chest", "Machine", "T2", "High", "Low", "Fixed path allows training to failure safely. Great for metabolic accumulation.")
    ex("Dumbbell Fly", "Chest", "Dumbbell", "T3", "Low", "Low", "Biases the lengthened position (stretch). keep elbows bent to protect shoulders.")
    ex("Cable Fly", "Chest", "Cable", "T3", "Low", "Low", "Provides constant tension especially at peak contraction (inner chest).")
    ex("Pec Deck", "Chest", "Machine", "T3", "Med", "Low", "Stable isolation. Removes stabilizer fatigue to focus purely on pectorals.")
    ex("Dips (Weighted)", "Chest", "Bodyweight", "T1", "High", "High", "Biases lower chest and triceps. Lean forward to emphasize chest.")
    ex("Push-Up", "Chest", "Bodyweight", "T3", "Low", "Low", "Closed kinetic chain. Excellent for shoulder health and high-rep finishers.")

    // ==================== SHOULDERS ====================
    ex("Overhead Press (Standing)", "Shoulders", "Barbell", "T1", "High", "High", "Primary vertical push. Builds raw strength and core stability.")
    ex("Seated Dumbbell Press", "Shoulders", "Dumbbell", "T1", "Med", "Med", "Hypertrophy staple. Anterior delt bias with greater ROM than barbell.")
    ex("Arnold Press", "Shoulders", "Dumbbell", "T2", "Med", "Med", "Hits front and side delts through rotation. increased time under tension.")
    ex("Machine Shoulder Press", "Shoulders", "Machine", "T2", "High", "Low", "Safe vertical push. Use for drop sets or fatigue work after free weights.")
    ex("Cable Lateral Raise", "Shoulders", "Cable", "T3", "Low", "Low", "Constant tension on side delts. Critical for creating shoulder width/cap.")
    ex("Face Pull", "Shoulders", "Cable", "T3", "Med", "Low", "Targets rear delts and external rotators. Vital for posture and shoulder health.")
    ex("Rear Delt Fly (Dumbbell)", "Shoulders", "Dumbbell", "T3", "Low", "Low", "Biases posterior delt. essential for the 3D look and joint balance.")
    ex("Reverse Pec Deck", "Shoulders", "Machine", "T3", "Med", "Low", "Stable rear delt isolation. Easier to execute than dumbbell variations.")
    ex("Upright Row", "Shoulders", "Barbell", "T2", "Med", "Med", "Compound movement for side delts and traps. Use wide grip to avoid impingement.")

    // ==================== BACK ====================
    ex("Deadlift", "Back", "Barbell", "T1", "High", "High", "Full posterior chain driver. High systemic fatigue; limits recovery resources.")
    ex("Pull-Up", "Back", "Bodyweight", "T1", "High", "High", "Primary vertical pull. Builds lat width and upper back density.")
    ex("Lat Pulldown", "Back", "Cable", "T2", "Med", "Low", "Scalable vertical pull. Focus on driving elbows down for lat width.")
    ex("Barbell Row", "Back", "Barbell", "T1", "High", "High", "Primary horizontal pull. Builds back thickness and erector strength.")
    ex("Dumbbell Row", "Back", "Dumbbell", "T2", "Med", "Med", "Unilateral row. Allows greater ROM and helps fix strength imbalances.")
    ex("Seated Cable Row", "Back", "Cable", "T2", "Med", "Low", "Mid-back bias. Good for targets rhomboids and traps without lower back strain.")
    ex("T-Bar Row", "Back", "Machine", "T1", "High", "Med", "Heavy supported row. Adds thickness with less shear force than barbell rows.")
    ex("Chest Supported Row", "Back", "Machine", "T2", "Med", "Low", "Strict isolation. Removes momentum to target lats or upper back precisely.")
    ex("Straight Arm Pulldown", "Back", "Cable", "T3", "Low", "Low", "Lat isolation (extension). Great pre-exhaust or finisher movement.")
    ex("Shrugs", "Back", "Dumbbell", "T3", "High", "Low", "Upper trap isolation. Builds the yoke.")

    // ==================== LEGS ====================
    ex("Squat (High Bar)", "Legs", "Barbell", "T1", "High", "High", "The king of leg movements. Biases quads/glutes with high systemic load.")
    ex("Front Squat", "Legs", "Barbell", "T1", "High", "High", "Upright torso biases quadriceps heavily. requires thoracic mobility.")
    ex("Leg Press", "Legs", "Machine", "T2", "High", "Med", "High loading capacity with no spinal compression. Ideal for volume.")
    ex("Hack Squat", "Legs", "Machine", "T1", "High", "Med", "Fixed path allows deep knee flexion. Excellent for quad hypertrophy.")
    ex("Bulgarian Split Squat", "Legs", "Dumbbell", "T2", "Med", "High", "Unilateral powerhouse. Fixes imbalances and challenges stability heavily.")
    ex("Romanian Deadlift", "Legs", "Barbell", "T1", "High", "Med", "Hip hinge focusing on hamstring stretch and glutes. Less fatigue than deadlift.")
    ex("Leg Extension", "Legs", "Machine", "T3", "Med", "Low", "Rectus femoris isolation. The only way to train quads in shortened position.")
    ex("Leg Curl (Seated)", "Legs", "Machine", "T3", "Med", "Low", "Hamstring isolation (knee flexion). Essential for knee joint health.")
    ex("Calf Raise (Standing)", "Legs", "Machine", "T3", "Med", "Low", "Targets gastrocnemius. Keep knee straight for maximum activation.")
    ex("Hip Thrust", "Legs", "Barbell", "T1", "High", "Med", "Direct glute max driver. Focuses on peak contraction (shortened position).")

    // ==================== ARMS ====================
    ex("Barbell Curl", "Arms", "Barbell", "T2", "High", "Low", "Mass builder for biceps. Allows heavy loading but can stress wrists.")
    ex("Dumbbell Curl", "Arms", "Dumbbell", "T3", "Med", "Low", "Supination activates peak contraction. The standard hypertrophy curl.")
    ex("Hammer Curl", "Arms", "Dumbbell", "T3", "High", "Low", "Targets brachialis and brachioradialis. Adds width to the arm.")
    ex("Preacher Curl", "Arms", "Machine", "T3", "Med", "Low", "Targets the short head. Focus on the stretch, avoid full lockout.")
    ex("Close Grip Bench", "Arms", "Barbell", "T1", "High", "Med", "Compound pusher. Allows heaviest loading for triceps overall mass.")
    ex("Skullcrusher", "Arms", "EZ_Bar", "T2", "Med", "Low", "Targets long head of triceps. Elbow position determines stretch focus.")
    ex("Cable Pushdown (Rope)", "Arms", "Cable", "T3", "Med", "Low", "Targets lateral head. Good for pump and joint health.")
    ex("Overhead Extension", "Arms", "Dumbbell", "T3", "Med", "Low", "Maximizes long head stretch. Crucial for massive triceps look.")

    // ==================== OLYMPIC LIFTING ====================
    ex("Barbell Snatch", "Full Body", "Barbell", "T1", "High", "High", "Highest technical demand. Always schedule first in the session.")
    ex("Clean and Jerk", "Full Body", "Barbell", "T1", "High", "High", "Primary test of strength-speed. Builds explosive power.")
    ex("Power Clean", "Full Body", "Barbell", "T1", "High", "Med", "Athletic power development. Focus on triple extension.")
    ex("Hang Clean", "Full Body", "Barbell", "T2", "High", "Med", "Focuses on the second pull and turnover speed.")
    ex("Overhead Squat", "Legs", "Barbell", "T2", "Med", "Med", "Ultimate test of core stability and mobility. light weight/high focus.")
    ex("Push Press", "Shoulders", "Barbell", "T2", "High", "Med", "Uses leg drive to overload the overhead lockout portion.")

    // ==================== CROSSFIT / FUNCTIONAL ====================
    ex("Thruster", "Full Body", "Barbell", "T1", "Med", "High", "High metabolic demand. Combines front squat and press.")
    ex("Handstand Push-Up", "Shoulders", "Bodyweight", "T1", "Med", "Med", "High skill gymnastics. Builds relative strength and balance.")
    ex("Box Jump", "Legs", "Bodyweight", "T2", "Low", "Med", "Plyometric power. Step down to save achilles tendons.")
    ex("Toes-to-Bar", "Core", "Bodyweight", "T2", "Low", "Med", "Gymnastic core work. requires grip strength and rhythm.")
    ex("Double Under", "Cardio", "Bodyweight", "T3", "Low", "High", "High intensity conditioning. Spikes heart rate quickly.")

    // ==================== POWERLIFTING SPECIFIC ====================
    ex("Pause Squat", "Legs", "Barbell", "T2", "High", "High", "Builds power out of the hole and reinforces bracing.")
    ex("Pin Squat", "Legs", "Barbell", "T2", "High", "High", "Overloads specific sticking points by limiting ROM.")
    ex("Spoto Press", "Chest", "Barbell", "T2", "High", "Med", "Pause one inch off chest. Builds reversal strength and stability.")
    ex("Deficit Deadlift", "Back", "Barbell", "T2", "High", "High", "Increases ROM to strengthen floor pull. Very taxing.")
    ex("Board Press", "Arms", "Barbell", "T2", "High", "Med", "Overloads the lockout (triceps). Allows supramaximal weight.")

    // ==================== KETTLEBELL ====================
    ex("Kettlebell Swing (Russian)", "Legs", "Kettlebell", "T1", "High", "Med", "Explosive hip hinge. Primary driver is glute max, not shoulders. Keep spine neutral.")
    ex("Goblet Squat", "Legs", "Kettlebell", "T2", "Med", "Med", "Front-loaded squat. Counterbalance helps achieve depth. Excellent for teaching squat mechanics.")
    ex("Turkish Get-Up", "Full Body", "Kettlebell", "T1", "High", "High", "Complex stabilizer. Massive time under tension for rotator cuff. Moves through multiple planes.")
    ex("Kettlebell Snatch", "Full Body", "Kettlebell", "T1", "High", "High", "High-intensity ballistic movement. Requires forceful hip extension and shoulder stability.")
    ex("Single Arm Clean & Press", "Shoulders", "Kettlebell", "T1", "High", "Med", "Unilateral power. Smooth transition from hip drive to overhead lockout. Fixes asymmetries.")
    ex("Kettlebell Halo", "Shoulders", "Kettlebell", "T3", "Low", "Low", "Dynamic mobility for shoulder girdle. Anti-extension core work. Keep ribs down.")
    ex("Farmers Carry", "Full Body", "Kettlebell", "T2", "High", "Med", "Moving plank. Challenges grip strength and postural alignment under heavy load.")
    ex("Single Leg RDL", "Legs", "Kettlebell", "T2", "Med", "Med", "Unilateral hip hinge. Challenges balance and fixes left/right hamstring strength imbalances.")
    ex("Gorilla Row", "Back", "Kettlebell", "T2", "Med", "Med", "Alternating row from dead stop. Reduces spinal shear force compared to barbell rows.")
    ex("Windmill", "Core", "Kettlebell", "T2", "Med", "Low", "Lateral flexion and stabilization. Increases hip mobility and shoulder resilience.")
}