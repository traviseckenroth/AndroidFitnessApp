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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Modern, rounded shapes
private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// Energetic Light Scheme based on Logo
private val CustomLightColorScheme = lightColorScheme(
    primary = FormaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = FormaBlueDark,
    secondary = FormaTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF00827B), // FormaCyanDark
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White
)

// Rich Dark Scheme based on Logo
private val CustomDarkColorScheme = darkColorScheme(
    primary = FormaAccentBlue,
    onPrimary = Color(0xFF04153B),
    primaryContainer = FormaBlueDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = FormaTealLight,
    onSecondary = Color(0xFF00302D),
    secondaryContainer = Color(0xFF00827B), // FormaCyanDark
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF1E2E4A),
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = ErrorRedLight,
    onError = Color(0xFF450A0A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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