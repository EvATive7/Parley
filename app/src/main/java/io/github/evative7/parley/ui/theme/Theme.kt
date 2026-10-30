package io.github.evative7.parley.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** True when the system "Remove animations" setting is on: no idle spin, no waves. */
val LocalReduceMotion = staticCompositionLocalOf { false }

// Fixed scheme for Android 8–11, generated from seed #4D5C92.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4D5C92), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF), onPrimaryContainer = Color(0xFF354479),
    secondary = Color(0xFF595D72), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9), onSecondaryContainer = Color(0xFF424659),
    tertiary = Color(0xFF75546F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F5), onTertiaryContainer = Color(0xFF5B3D57),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC), onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680), outlineVariant = Color(0xFFC6C5D0),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF2F0F7),
    inversePrimary = Color(0xFFB6C4FF),
    surfaceDim = Color(0xFFDBD9E0), surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF), onPrimary = Color(0xFF1D2D61),
    primaryContainer = Color(0xFF354479), onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC2C5DD), onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659), onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFE3BADA), onTertiary = Color(0xFF43273F),
    tertiaryContainer = Color(0xFF5B3D57), onTertiaryContainer = Color(0xFFFFD7F5),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E1E9), inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF4D5C92),
    surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)

@Composable
fun ParleyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}
