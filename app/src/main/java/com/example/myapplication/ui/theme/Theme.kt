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
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Defined for the Pro/Compact feel
private val CompactShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// Premium Stealth Light Scheme
private val CustomLightColorScheme = lightColorScheme(
    primary = PrimaryAccent,          // Carbon Black
    onPrimary = Color.White,          // White text on black buttons
    primaryContainer = Color(0xFFE0E0E0), // Soft grey for containers
    onPrimaryContainer = PrimaryAccent, // Black text on grey containers
    secondary = SecondaryAccent,      // Slate Charcoal
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5F5F5), // Very light grey
    onSecondaryContainer = SecondaryAccent,
    background = StudioBackground,    // Neutral off-white
    surface = StudioSurface,          // Pure white
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0), // Standard grey for cards
    onSurfaceVariant = NeutralGrey,
    outline = SubtleOutline,
    error = Color(0xFFD32F2F),        // Standard Material Error Red
    onError = Color.White
)

// Premium Stealth Dark Scheme
private val CustomDarkColorScheme = darkColorScheme(
    primary = Color.White,            // White buttons in dark mode
    onPrimary = Color.Black,          // Black text on white buttons
    primaryContainer = Color(0xFF2C2C2C), // Dark grey containers
    onPrimaryContainer = Color.White, // White text on dark grey
    secondary = Color(0xFFA0A0A0),    // Light charcoal
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF121212),   // Deep carbon background
    surface = Color(0xFF1A1A1A),      // Slightly elevated dark surface
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false to enforce your stealth branding
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

            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)

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