package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Defined for the Pro/Compact feel (Matching the fluid cards in your screenshots)
private val CompactShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// Optimized Dark Scheme for High-Performance Fitness Look
private val CustomDarkColorScheme = darkColorScheme(
    primary = NeonLime,        // The bright #D7FF42 from your image
    onPrimary = Color.Black,
    secondary = NeonVolt,      // A secondary accent if needed
    onSecondary = Color.Black,
    background = DeepBlack,    // Pure #000000
    surface = SurfaceGrey,     // Slightly elevated grey for cards
    surfaceVariant = Gunmetal,
    onSurface = WhitePrimary,
    onSurfaceVariant = GreySecondary,
    outline = Charcoal
)

// Defining LightColorScheme to resolve the unresolved reference
private val CustomLightColorScheme = lightColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,
    background = Color.White,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Enforce brand colors
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
            // Using toArgb() for system bars
            window.statusBarColor = Color.Black.toArgb()
            window.navigationBarColor = Color.Black.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)
            // For a dark theme, we want light icons on the status bar
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CompactShapes,
        content = content
    )
}