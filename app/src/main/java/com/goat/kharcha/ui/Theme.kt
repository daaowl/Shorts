package com.goat.kharcha.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.goat.kharcha.R
import com.goat.kharcha.core.ThemeMode

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val fg: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val scrim: Color,
    val dark: Boolean,
)

val DarkPalette = Palette(
    bg = Color(0xFF0B0E13), surface = Color(0xFF141922), surface2 = Color(0xFF1B222D), surface3 = Color(0xFF232B38),
    line = Color(0xFF252E3B), fg = Color(0xFFE9EDF3), muted = Color(0xFF8A94A6), faint = Color(0xFF5D6676),
    accent = Color(0xFFA7B2FF), onAccent = Color(0xFF0C0F14), good = Color(0xFF6CD4A2), warn = Color(0xFFF2BA4E),
    bad = Color(0xFFF57D80), scrim = Color(0x99040609), dark = true,
)

val LightPalette = Palette(
    bg = Color(0xFFF3F4F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFECEEF5), surface3 = Color(0xFFE2E6EF),
    line = Color(0xFFDCE0EA), fg = Color(0xFF131722), muted = Color(0xFF5C6578), faint = Color(0xFF8B93A3),
    accent = Color(0xFF4C58D2), onAccent = Color(0xFFFFFFFF), good = Color(0xFF1C9862), warn = Color(0xFFB07316),
    bad = Color(0xFFD3434A), scrim = Color(0x59141822), dark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Display = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)
val Body = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_medium, FontWeight.Medium),
    Font(R.font.jetbrains_semibold, FontWeight.SemiBold),
)

/** The type scale. Every text in the app uses one of these. */
object T {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-1.6).sp, fontFeatureSettings = "tnum")
    val amountXl = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = "tnum")
    val h1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp)
    val stat = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")
    val title = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
    val chip = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 16.sp)
    val num = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val numSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum")
    val button = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp)
}

/** Category colors are tuned for dark backgrounds; on light ones they're deepened to keep contrast. */
fun catColor(argb: Long, dark: Boolean): Color {
    val c = Color(argb)
    return if (dark) c else lerp(c, Color.Black, 0.22f)
}

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun KharchaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.fg, onBackground = p.fg, outline = p.line, surfaceVariant = p.surface2)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.fg, onBackground = p.fg, outline = p.line, surfaceVariant = p.surface2)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme) {
            ProvideTextStyle(T.body.copy(color = p.fg), content = content)
        }
    }
}
