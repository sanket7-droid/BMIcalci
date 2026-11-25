package com.example.bmicalci.ui.theme

import androidx.compose.ui.text.font.Font
import com.example.bmicalci.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Design tokens: colors */
object Neon {
    // Neutral near-black surfaces + ONE accent (kept under the old names so every screen restyles at once).
    val Bg = Color(0xFF0A0A0B)
    val BgTop = Color(0xFF0A0A0B)
    val Surface = Color(0xFF141416)
    val SurfaceHigh = Color(0xFF1C1C1F)
    val Border = Color(0xFF2A2A2E)

    val Accent = Color(0xFFF4F4F5)      // monochrome primary (white buttons/chips, Nike-style)
    val OnAccent = Color(0xFF0A0A0B)
    val Cyan = Color(0xFFB8BBC2)        // labels / secondary emphasis
    val Green = Color(0xFF3DDC84)
    val Orange = Color(0xFFFFB020)
    val Red = Color(0xFFFF4D4D)
    val Yellow = Color(0xFFFFC83D)
    val Purple = Color(0xFFB39DDB)
    val Blue = Color(0xFF9FB3C8)

    val TextPrimary = Color(0xFFF5F6F7)
    val TextSecondary = Color(0xFFA7ADB6)
    val TextMuted = Color(0xFF7D838D)

    // BMI semantic colours
    val Underweight = Yellow
    val Healthy = Green
    val Overweight = Orange
    val Obese = Red

    val ScreenGradient = Brush.verticalGradient(listOf(Bg, Bg))
    val GlowGradient = Brush.horizontalGradient(listOf(Accent, Accent))
}

/** Design tokens: spacing */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Design tokens: shapes */
object Radius {
    val card = RoundedCornerShape(16.dp)
    val field = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
}

// Bundled Barlow so the look is identical on every device (some OEM system fonts are decorative).
private val HeadingFamily = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))
private val BodyFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_regular, FontWeight.Medium),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp),
    headlineMedium = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.8.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

private val NeonColorScheme = darkColorScheme(
    primary = Neon.Accent,
    onPrimary = Neon.OnAccent,
    secondary = Neon.Green,
    onSecondary = Color(0xFF05210F),
    tertiary = Neon.Orange,
    background = Neon.Bg,
    onBackground = Neon.TextPrimary,
    surface = Neon.Surface,
    onSurface = Neon.TextPrimary,
    surfaceVariant = Neon.SurfaceHigh,
    onSurfaceVariant = Neon.TextSecondary,
    outline = Neon.Border,
    error = Neon.Red,
)

@Composable
fun BmiHealthProTheme(content: @Composable () -> Unit) {
    // The app is intentionally dark-only (neon look).
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = AppTypography,
        shapes = Shapes(
            small = Radius.field,
            medium = Radius.field,
            large = Radius.card,
        ),
        content = content,
    )
}
