package com.example.myapplication.data.local

suspend fun populateDatabase(dao: WorkoutDao) {

    if (dao.getAllExercisesOneShot().isNotEmpty()) return

    dao.deleteAllExercises()

    // UPDATED SIGNATURE: Added 'desc' parameter
    suspend fun ex(name: String, group: String, equip: String, tierString: String, load: String, fatigue: String, note: String, desc: String) {
        val tierInt = when {
            tierString.contains("1") -> 1
            tierString.contains("2") -> 2
            else -> 3
        }
        val timeVal = when (tierString) {
            "T1" -> 3.0
            "T2" -> 2.5
            else -> 2.0
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
                description = desc,
                estimatedTimePerSet = timeVal
            )
        )
    }

// ==================== CHEST ====================
    ex("Barbell Bench Press", "Chest", "Barbell", "T1", "High", "High", "Primary horizontal push for max loading. Targets sternal pecs and triceps.",
        "Lie on bench, eyes under bar. Grip slightly wider than shoulders. Retract scapula. Lower bar to mid-chest (sternum) with elbows at 45 degrees. Press back up to lockout.")

    ex("Dumbbell Bench Press", "Chest", "Dumbbell", "T1", "Med", "Med", "Increases range of motion over barbell. excellent for fixing asymmetry.",
        "Lie on bench with dumbbells at shoulders. Press up, converging slightly at the top without touching weights together. Lower until heavy stretch is felt in pecs.")

    ex("Incline Barbell Press", "Chest", "Barbell", "T1", "High", "High", "Biases clavicular head (upper chest). Essential for full shelf development.",
        "Set bench to 30-45 degrees. Grip shoulder-width. Lower bar to upper chest (just below collarbone). Press vertically. Keep elbows tucked.")

    ex("Incline Dumbbell Press", "Chest", "Dumbbell", "T2", "Med", "Med", "Best movement for upper chest hypertrophy due to deep stretch at bottom.",
        "Set bench to 30 degrees. Press dumbbells overhead. Lower deeply to stretch upper pecs, keeping forearms vertical. Press up and slightly in.")

    ex("Machine Chest Press", "Chest", "Machine", "T2", "High", "Low", "Fixed path allows training to failure safely. Great for metabolic accumulation.",
        "Adjust seat so handles align with mid-chest. Press handles forward until arms extend. Control the negative slowly back to starting position.")

    ex("Dumbbell Fly", "Chest", "Dumbbell", "T3", "Low", "Low", "Biases the lengthened position (stretch). keep elbows bent to protect shoulders.",
        "Lie on bench. Start with weights above chest, palms facing each other. Lower weights in a wide arc with slight elbow bend until chest stretches. Squeeze back to top.")

    ex("Cable Fly", "Chest", "Cable", "T3", "Low", "Low", "Provides constant tension especially at peak contraction (inner chest).",
        "Set pulleys to shoulder height. Step forward with staggered stance. Drive handles together in front of chest, squeezing hard. Control the return.")

    ex("Pec Deck", "Chest", "Machine", "T3", "Med", "Low", "Stable isolation. Removes stabilizer fatigue to focus purely on pectorals.",
        "Adjust seat so elbows differ slightly below shoulders. Place forearms on pads. Squeeze elbows together in front of you. Resist the return.")

    ex("Dips (Weighted)", "Chest", "Bodyweight", "T1", "High", "High", "Biases lower chest and triceps. Lean forward to emphasize chest.",
        "Mount dip bars. Lean torso forward 30 degrees. Lower body until shoulders are below elbows. Press back up, keeping tension on chest, not shoulders.")

    ex("Push-Up", "Chest", "Bodyweight", "T3", "Low", "Low", "Closed kinetic chain. Excellent for shoulder health and high-rep finishers.",
        "Plank position, hands shoulder-width. Lower chest to floor, keeping elbows tucked 45 degrees. Press up, protracting shoulder blades at the top.")

// ==================== SHOULDERS ====================
    ex("Overhead Press (Standing)", "Shoulders", "Barbell", "T1", "High", "High", "Primary vertical push. Builds raw strength and core stability.",
        "Stand with bar on front delts. Grip just outside shoulders. Brace core and glutes. Move head back, press bar vertically over spine. Lock out and shrug.")

    ex("Seated Dumbbell Press", "Shoulders", "Dumbbell", "T1", "Med", "Med", "Hypertrophy staple. Anterior delt bias with greater ROM than barbell.",
        "Sit on bench with back support. Start with dumbbells at ear level. Press vertically until arms extend. Lower under control to ear level.")

    ex("Arnold Press", "Shoulders", "Dumbbell", "T2", "Med", "Med", "Hits front and side delts through rotation. increased time under tension.",
        "Start with palms facing you at shoulder height. Press up while rotating palms forward. Lockout with palms facing away. Reverse rotation on descent.")

    ex("Machine Shoulder Press", "Shoulders", "Machine", "T2", "High", "Low", "Safe vertical push. Use for drop sets or fatigue work after free weights.",
        "Adjust seat so handles align with ears. Grip handles and press vertically. Lower slowly without letting weight stack touch.")

    ex("Cable Lateral Raise", "Shoulders", "Cable", "T3", "Low", "Low", "Constant tension on side delts. Critical for creating shoulder width/cap.",
        "Set pulley to bottom. Stand sideways. Grip handle with far hand. Raise arm out to side until parallel to floor. Control the negative.")

    ex("Face Pull", "Shoulders", "Cable", "T3", "Med", "Low", "Targets rear delts and external rotators. Vital for posture and shoulder health.",
        "Set rope at eye level. Pull rope towards face, separating hands to pass ears. Externally rotate shoulders at end range. Squeeze rear delts.")

    ex("Rear Delt Fly (Dumbbell)", "Shoulders", "Dumbbell", "T3", "Low", "Low", "Biases posterior delt. essential for the 3D look and joint balance.",
        "Hinge at hips until torso is parallel to floor. Let arms hang. Raise dumbbells out to sides using rear delts, not traps. Lower slowly.")

    ex("Reverse Pec Deck", "Shoulders", "Machine", "T3", "Med", "Low", "Stable rear delt isolation. Easier to execute than dumbbell variations.",
        "Face the machine. Grip handles with arms straight. Pull arms back in a wide arc until inline with body. Squeeze rear delts. Return slowly.")

    ex("Upright Row", "Shoulders", "Barbell", "T2", "Med", "Med", "Compound movement for side delts and traps. Use wide grip to avoid impingement.",
        "Hold bar with wide grip (outside thighs). Pull elbows up and out to shoulder height. Keep bar close to body. Lower under control.")

// ==================== BACK ====================
    ex("Deadlift", "Back", "Barbell", "T1", "High", "High", "Full posterior chain driver. High systemic fatigue; limits recovery resources.",
        "Mid-foot under bar. Hinge hips back. Grip bar. Pull slack out until you hear a 'click'. Drive feet into floor to stand up. Lock hips, do not hyperextend.")

    ex("Pull-Up", "Back", "Bodyweight", "T1", "High", "High", "Primary vertical pull. Builds lat width and upper back density.",
        "Hang from bar with overhand grip slightly wider than shoulders. Drive elbows down to ribs to pull chin over bar. Lower to full dead hang.")

    ex("Lat Pulldown", "Back", "Cable", "T2", "Med", "Low", "Scalable vertical pull. Focus on driving elbows down for lat width.",
        "Sit with knees secured. Grip wide. Lean back slightly. Pull bar to upper chest, driving elbows down and back. Squeeze lats. Release slowly.")

    ex("Barbell Row", "Back", "Barbell", "T1", "High", "High", "Primary horizontal pull. Builds back thickness and erector strength.",
        "Hinge forward 45-90 degrees. Grip bar. Pull bar to lower chest/upper stomach. Squeeze shoulder blades together. Lower to full arm extension.")

    ex("Dumbbell Row", "Back", "Dumbbell", "T2", "Med", "Med", "Unilateral row. Allows greater ROM and helps fix strength imbalances.",
        "Place one knee and hand on bench. Flat back. Pull dumbbell to hip pocket, driving elbow back. Lower until a deep stretch is felt in lat.")

    ex("Seated Cable Row", "Back", "Cable", "T2", "Med", "Low", "Mid-back bias. Good for targets rhomboids and traps without lower back strain.",
        "Sit with feet braced. Grip attachment. Keep torso upright. Pull handle to stomach, retracting scapula fully. Extend arms forward to stretch lats.")

    ex("T-Bar Row", "Back", "Machine", "T1", "High", "Med", "Heavy supported row. Adds thickness with less shear force than barbell rows.",
        "Straddle the bar. Hinge at hips. Grip handles. Pull weight to chest while keeping spine neutral. Lower carefully.")

    ex("Chest Supported Row", "Back", "Machine", "T2", "Med", "Low", "Strict isolation. Removes momentum to target lats or upper back precisely.",
        "Adjust chest pad height. Grip handles. Pull elbows back hard while keeping chest glued to the pad. Squeeze back. Return to start.")

    ex("Straight Arm Pulldown", "Back", "Cable", "T3", "Low", "Low", "Lat isolation (extension). Great pre-exhaust or finisher movement.",
        "Stand facing rope attachment. Arms straight, hinge slightly. Push bar/rope down to hips using lats. Keep elbows locked. return to eye level.")

    ex("Shrugs", "Back", "Dumbbell", "T3", "High", "Low", "Upper trap isolation. Builds the yoke.",
        "Hold heavy dumbbells at sides. Elevate shoulders towards ears as high as possible. Pause at top. Lower fully.")

// ==================== LEGS ====================
    ex("Squat (High Bar)", "Legs", "Barbell", "T1", "High", "High", "The king of leg movements. Biases quads/glutes with high systemic load.",
        "Place bar on traps. Feet shoulder-width. Brace core. Break at hips and knees simultaneously. Descend until hip crease is below knee. Drive up.")

    ex("Front Squat", "Legs", "Barbell", "T1", "High", "High", "Upright torso biases quadriceps heavily. requires thoracic mobility.",
        "Rack bar on front delts with crossed arms or clean grip. Keep elbows high. Squat deep while maintaining upright torso. Drive up through heels.")

    ex("Leg Press", "Legs", "Machine", "T2", "High", "Med", "High loading capacity with no spinal compression. Ideal for volume.",
        "Sit in machine. Place feet hip-width on platform. Lower weight until knees are near chest (ensure lower back stays flat). Press back up.")

    ex("Hack Squat", "Legs", "Machine", "T1", "High", "Med", "Fixed path allows deep knee flexion. Excellent for quad hypertrophy.",
        "Shoulders under pads. Feet lower on platform for quad bias. Unlock safeties. Squat deep. Drive up through legs without locking knees.")

    ex("Bulgarian Split Squat", "Legs", "Dumbbell", "T2", "Med", "High", "Unilateral powerhouse. Fixes imbalances and challenges stability heavily.",
        "Place rear foot on bench. Step front foot forward. Lower hips straight down until back knee almost touches floor. Drive up through front heel.")

    ex("Romanian Deadlift", "Legs", "Barbell", "T1", "High", "Med", "Hip hinge focusing on hamstring stretch and glutes. Less fatigue than deadlift.",
        "Start standing with bar. Unlock knees slightly. Hinge hips BACK as far as possible while sliding bar down thighs. Feel hamstring stretch. Drive hips forward.")

    ex("Leg Extension", "Legs", "Machine", "T3", "Med", "Low", "Rectus femoris isolation. The only way to train quads in shortened position.",
        "Adjust pad to ankle. Sit back. Extend knees until legs are straight. Squeeze quads hard at the top. Lower under control.")

    ex("Leg Curl (Seated)", "Legs", "Machine", "T3", "Med", "Low", "Hamstring isolation (knee flexion). Essential for knee joint health.",
        "Adjust lap pad tight. Legs straight on pad. Curl heels down and under seat. Squeeze hamstrings. Return slowly to start.")

    ex("Calf Raise (Standing)", "Legs", "Machine", "T3", "Med", "Low", "Targets gastrocnemius. Keep knee straight for maximum activation.",
        "Shoulders under pads. Balls of feet on step. Lower heels as far as possible for stretch. Press up onto big toes. Squeeze top.")

    ex("Hip Thrust", "Legs", "Barbell", "T1", "High", "Med", "Direct glute max driver. Focuses on peak contraction (shortened position).",
        "Back against bench. Bar over hips. Drive hips up until body is flat table-top. Squeeze glutes hard. Chin tucked. Lower hips to floor.")

// ==================== ARMS ====================
    ex("Barbell Curl", "Arms", "Barbell", "T2", "High", "Low", "Mass builder for biceps. Allows heavy loading but can stress wrists.",
        "Stand tall. Grip bar shoulder-width, palms up. Curl bar to chest keeping elbows at sides. Lower fully to thighs.")

    ex("Dumbbell Curl", "Arms", "Dumbbell", "T3", "Med", "Low", "Supination activates peak contraction. The standard hypertrophy curl.",
        "Stand with dumbbells. Curl up while rotating pinky finger towards ceiling. Squeeze bicep. Lower and rotate back to neutral.")

    ex("Hammer Curl", "Arms", "Dumbbell", "T3", "High", "Low", "Targets brachialis and brachioradialis. Adds width to the arm.",
        "Hold dumbbells with neutral grip (palms facing body). Curl weight up without rotating wrist. Lower slowly.")

    ex("Preacher Curl", "Arms", "Machine", "T3", "Med", "Low", "Targets the short head. Focus on the stretch, avoid full lockout.",
        "Armpits over pad. Arms extended. Curl weight up without lifting elbows off pad. Lower slowly to full stretch.")

    ex("Close Grip Bench", "Arms", "Barbell", "T1", "High", "Med", "Compound pusher. Allows heaviest loading for triceps overall mass.",
        "Setup like bench press but grip shoulder-width. Lower bar to lower chest, keeping elbows tucked close to torso. Press up.")

    ex("Skullcrusher", "Arms", "EZ_Bar", "T2", "Med", "Low", "Targets long head of triceps. Elbow position determines stretch focus.",
        "Lie on bench. Hold EZ bar above chest. Bend elbows to lower bar to forehead/behind head. Extend arms back to start.")

    ex("Cable Pushdown (Rope)", "Arms", "Cable", "T3", "Med", "Low", "Targets lateral head. Good for pump and joint health.",
        "Attach rope. Keep elbows pinned to sides. Extend elbows to separate rope at the bottom. Squeeze triceps. Return to 90 degrees.")

    ex("Overhead Extension", "Arms", "Dumbbell", "T3", "Med", "Low", "Maximizes long head stretch. Crucial for massive triceps look.",
        "Sit or stand. Hold one dumbbell with both hands overhead. Lower weight behind head for deep stretch. Press back up to ceiling.")

// ==================== OLYMPIC LIFTING ====================
    ex("Barbell Snatch", "Full Body", "Barbell", "T1", "High", "High", "Highest technical demand. Always schedule first in the session.",
        "Wide grip. Hips low. Pull bar from floor, accelerate past knees, explode hips (contact), pull under bar, catch in overhead squat.")

    ex("Clean and Jerk", "Full Body", "Barbell", "T1", "High", "High", "Primary test of strength-speed. Builds explosive power.",
        "Clean: Pull bar, extend hips, catch in front squat. Stand. Jerk: Dip, drive bar overhead while splitting legs. Recover feet.")

    ex("Power Clean", "Full Body", "Barbell", "T1", "High", "Med", "Athletic power development. Focus on triple extension.",
        "Setup like deadlift. Explode hips forward. Shrug and pull under bar. Catch on shoulders in quarter squat position. Stand up.")

    ex("Hang Clean", "Full Body", "Barbell", "T2", "High", "Med", "Focuses on the second pull and turnover speed.",
        "Start standing with bar. Lower to just above knees. Explode hips forward, shrug, and catch on shoulders.")

    ex("Overhead Squat", "Legs", "Barbell", "T2", "Med", "Med", "Ultimate test of core stability and mobility. light weight/high focus.",
        "Press bar overhead with wide snatch grip. Lock elbows active. Squat deep while keeping bar over mid-foot. Drive up.")

    ex("Push Press", "Shoulders", "Barbell", "T2", "High", "Med", "Uses leg drive to overload the overhead lockout portion.",
        "Rack bar on shoulders. Dip knees slightly (torso vertical). Drive up with legs and finish with arm press overhead. Lockout.")

// ==================== CROSSFIT / FUNCTIONAL ====================
    ex("Thruster", "Full Body", "Barbell", "T1", "Med", "High", "High metabolic demand. Combines front squat and press.",
        "Front rack position. Perform full front squat. As you stand, use momentum to press bar overhead in one fluid motion.")

    ex("Handstand Push-Up", "Shoulders", "Bodyweight", "T1", "Med", "Med", "High skill gymnastics. Builds relative strength and balance.",
        "Kick up to wall handstand. Lower head to floor. Press back up to lockout. Keep core tight and bodyline straight.")

    ex("Box Jump", "Legs", "Bodyweight", "T2", "Low", "Med", "Plyometric power. Step down to save achilles tendons.",
        "Stand facing box. Swing arms and dip hips. Explode up, landing softly on box with both feet. Stand tall. Step down.")

    ex("Toes-to-Bar", "Core", "Bodyweight", "T2", "Low", "Med", "Gymnastic core work. requires grip strength and rhythm.",
        "Hang from bar. Kip slightly. Snap legs up to touch toes to the bar between hands. Return to hollow body position.")

    ex("Double Under", "Cardio", "Bodyweight", "T3", "Low", "High", "High intensity conditioning. Spikes heart rate quickly.",
        "Jump rope where rope passes twice under feet per jump. Jump higher and spin wrists faster than single unders.")

// ==================== POWERLIFTING SPECIFIC ====================
    ex("Pause Squat", "Legs", "Barbell", "T2", "High", "High", "Builds power out of the hole and reinforces bracing.",
        "Perform squat but stop at bottom for 2 second count. Explode up. Do not bounce out of the hole.")

    ex("Pin Squat", "Legs", "Barbell", "T2", "High", "High", "Overloads specific sticking points by limiting ROM.",
        "Set safety pins in rack to desired depth. Squat down until bar rests on pins. Pause. Drive up from dead stop.")

    ex("Spoto Press", "Chest", "Barbell", "T2", "High", "Med", "Pause one inch off chest. Builds reversal strength and stability.",
        "Bench press but stop bar 1-2 inches above chest. Pause for 1 second. Press up. Maintain tension, do not relax.")

    ex("Deficit Deadlift", "Back", "Barbell", "T2", "High", "High", "Increases ROM to strengthen floor pull. Very taxing.",
        "Stand on 1-2 inch plate/platform. Perform conventional deadlift. Focus on leg drive to break floor.")

    ex("Board Press", "Arms", "Barbell", "T2", "High", "Med", "Overloads the lockout (triceps). Allows supramaximal weight.",
        "Place 1-3 board block on chest. Lower bar to touch board. Press up. Focuses on top range of motion.")

// ==================== KETTLEBELL ====================
    ex("Kettlebell Swing (Russian)", "Legs", "Kettlebell", "T1", "High", "Med", "Explosive hip hinge. Primary driver is glute max, not shoulders. Keep spine neutral.",
        "Hinge hips back, hiking KB between legs. Snap hips forward explosively to float KB to chest height. Do not pull with arms.")

    ex("Goblet Squat", "Legs", "Kettlebell", "T2", "Med", "Med", "Front-loaded squat. Counterbalance helps achieve depth. Excellent for teaching squat mechanics.",
        "Hold KB by horns at chest. Squat deep, elbows inside knees. Keep chest up. Drive up through heels.")

    ex("Turkish Get-Up", "Full Body", "Kettlebell", "T1", "High", "High", "Complex stabilizer. Massive time under tension for rotator cuff. Moves through multiple planes.",
        "Lie on back, KB pressed up. Roll to elbow, then hand. Bridge hips, sweep leg under. Lunge up to stand. Reverse perfectly.")

    ex("Kettlebell Snatch", "Full Body", "Kettlebell", "T1", "High", "High", "High-intensity ballistic movement. Requires forceful hip extension and shoulder stability.",
        "One-arm swing setup. Hips drive KB up. Punch hand through handle at top to lock out overhead without banging wrist.")

    ex("Single Arm Clean & Press", "Shoulders", "Kettlebell", "T1", "High", "Med", "Unilateral power. Smooth transition from hip drive to overhead lockout. Fixes asymmetries.",
        "Clean KB to rack position (thumb to chest). Press overhead. Lower to rack, then floor.")

    ex("Kettlebell Halo", "Shoulders", "Kettlebell", "T3", "Low", "Low", "Dynamic mobility for shoulder girdle. Anti-extension core work. Keep ribs down.",
        "Hold KB bottoms-up by horns. Circle it tightly around head. Keep core braced and ribs down. Do not arch back.")

    ex("Farmers Carry", "Full Body", "Kettlebell", "T2", "High", "Med", "Moving plank. Challenges grip strength and postural alignment under heavy load.",
        "Deadlift two heavy KBs. Walk with quick, short steps. Shoulders back, core tight. Do not let weights swing.")

    ex("Single Leg RDL", "Legs", "Kettlebell", "T2", "Med", "Med", "Unilateral hip hinge. Challenges balance and fixes left/right hamstring strength imbalances.",
        "Hold KB in opposite hand to standing leg. Hinge hip back while lifting other leg straight back. Torso/leg form straight line. Return.")

    ex("Gorilla Row", "Back", "Kettlebell", "T2", "Med", "Med", "Alternating row from dead stop. Reduces spinal shear force compared to barbell rows.",
        "Wide stance, two KBs on floor. Hinge down. Row one KB while pushing the other into floor. Alternate sides.")

    ex("Windmill", "Core", "Kettlebell", "T2", "Med", "Low", "Lateral flexion and stabilization. Increases hip mobility and shoulder resilience.",
        "Press KB overhead. Look at KB. Hinge at hips, sliding free hand down inside of leg towards floor. Keep back leg straight. Stand up.")

}