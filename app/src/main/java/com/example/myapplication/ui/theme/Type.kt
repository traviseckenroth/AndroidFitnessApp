// app/src/main/java/com/example/myapplication/ui/theme/Type.kt

package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Minimalist Studio Typography ---
// Prioritizes hierarchy through weight and scale rather than decorative fonts.

val Typography = Typography(
    // 1. Screen Titles (e.g., "My Fitness", "Settings")
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // 2. Section Headers (e.g., "TODAY'S SCHEDULE", "QUICK ACTIONS")
    // Using widely spaced caps for a technical, clean look
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.25.sp // Increased tracking
    ),

    // 3. Card Titles (e.g., "Bench Press", "Workout Summary")
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // 4. Subtitles (e.g., "3 Sets x 5 Reps", "Intermediate Protocol")
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    // 5. Primary Content (e.g., Instructions, Descriptions)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    // 6. Secondary Content (e.g., "Estimated 1RM", small helper text)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    // 7. Data & Timers (e.g., "45", "01:30")
    // Uses Monospace for numbers to ensure alignment in tables
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    )
)