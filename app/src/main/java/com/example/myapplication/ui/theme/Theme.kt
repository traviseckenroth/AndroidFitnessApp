package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Modern, rounded shapes
private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// Energetic Light Scheme
private val CustomLightColorScheme = lightColorScheme(
    primary = FormaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),     // Very soft blue background for chips/containers
    onPrimaryContainer = FormaBlueDark,
    secondary = FormaTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),   // Very soft teal
    onSecondaryContainer = Color(0xFF115E59),
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),       // Slightly darker than background for secondary cards
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White
)

// Rich Dark Scheme (Slate-based, not pure black)
private val CustomDarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),              // Lighter, brighter blue for dark mode visibility
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = FormaBlueDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF2DD4BF),            // Brighter teal for dark mode
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = FormaTeal,
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false to enforce your brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CustomDarkColorScheme
        else -> CustomLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
