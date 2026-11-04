package ru.akkordnik.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.akkordnik.app.R

@Immutable
data class Pal(
    val bg: Color, val surface: Color, val raised: Color, val text: Color, val accent: Color,
    val a1: Color, val a2: Color, val onGrad: Color, val sec: Color, val line: Color,
    val g1: Color, val g2: Color, val danger: Color, val light: Boolean,
) {
    val muted get() = text.copy(alpha = .64f)
    val grad get() = Brush.linearGradient(listOf(a1, a2))
    fun gradH() = Brush.horizontalGradient(listOf(a1, a2))
}

private fun c(hex: Long) = Color(hex)

val THEMES: Map<String, Pal> = mapOf(
    "sunset" to Pal(c(0xFF120D0B), c(0xFF1D1613), c(0xFF2A201B), c(0xFFFFF4EA), c(0xFFFFB547), c(0xFFFF9A3D), c(0xFFFF3D6E),
        c(0xFF1A0B05), c(0xFFFF6B85), Color(255, 236, 220, 28), Color(255, 128, 50, 87), Color(255, 50, 110, 66), c(0xFFFF6B6B), false),
    "night" to Pal(c(0xFF0A0A1C), c(0xFF14142C), c(0xFF1E1E3E), c(0xFFF5F6FF), c(0xFF4ADE80), c(0xFFA06BFF), c(0xFF5B5FF0),
        c(0xFFFFFFFF), c(0xFFA5B4FC), Color(200, 205, 255, 31), Color(110, 90, 255, 92), Color(34, 197, 94, 51), c(0xFFFF6B6B), false),
    "ocean" to Pal(c(0xFF061318), c(0xFF0C1F27), c(0xFF142F3A), c(0xFFEFFBFF), c(0xFF5EEAD4), c(0xFF2BD9F0), c(0xFF3B82F6),
        c(0xFF031018), c(0xFF7DD3FC), Color(190, 240, 255, 31), Color(34, 211, 238, 77), Color(59, 130, 246, 71), c(0xFFFF6B6B), false),
    "neon" to Pal(c(0xFF11061A), c(0xFF1D0E2A), c(0xFF2B163C), c(0xFFFFF2FD), c(0xFFFF7AC6), c(0xFFE048FB), c(0xFFFF4D8D),
        c(0xFF1A0614), c(0xFFD29BFF), Color(250, 210, 255, 31), Color(224, 72, 251, 82), Color(255, 77, 141, 66), c(0xFFFF6B6B), false),
    "paper" to Pal(c(0xFFFBF6EF), c(0xFFFFFFFF), c(0xFFF2EADF), c(0xFF221912), c(0xFFC2410C), c(0xFFFB923C), c(0xFFF43F5E),
        c(0xFF1F0A04), c(0xFFBE123C), Color(60, 40, 20, 31), Color(251, 146, 60, 66), Color(244, 63, 94, 41), c(0xFFC81E1E), true),
)
val THEME_NAMES = listOf("sunset" to "Закат", "night" to "Ночь", "ocean" to "Океан", "neon" to "Неон", "paper" to "Бумага")

val LocalPal = staticCompositionLocalOf { THEMES.getValue("sunset") }
val LocalMotion = staticCompositionLocalOf { true }
/** Момент появления текущего экрана: анимации входа играют только в первые мгновения, а не при каждой прокрутке. */
val LocalScreenStart = staticCompositionLocalOf { 0L }

@Composable
fun animatedPal(target: Pal, motion: Boolean): Pal {
    val spec = tween<Color>(if (motion) 420 else 0)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "pal").value
    return Pal(
        a(target.bg), a(target.surface), a(target.raised), a(target.text), a(target.accent), a(target.a1), a(target.a2),
        a(target.onGrad), a(target.sec), a(target.line), a(target.g1), a(target.g2), a(target.danger), target.light
    )
}

@Composable
fun AkkTheme(theme: String, motion: Boolean, content: @Composable () -> Unit) {
    val pal = animatedPal(THEMES[theme] ?: THEMES.getValue("sunset"), motion)
    CompositionLocalProvider(LocalPal provides pal, LocalMotion provides motion, content = content)
}

// ---------- шрифты ----------
// Статичные начертания (вырезаны из variable-шрифтов) — одинаково выглядят на любом Android
val Manrope = FontFamily(
    Font(R.font.manrope_500, FontWeight.Medium), Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold), Font(R.font.manrope_800, FontWeight.ExtraBold)
)
val Unbounded = FontFamily(Font(R.font.unbounded_700, FontWeight.Bold), Font(R.font.unbounded_800, FontWeight.ExtraBold))
val Mono = FontFamily(Font(R.font.jetbrains_mono_500, FontWeight.Medium), Font(R.font.jetbrains_mono_800, FontWeight.ExtraBold))

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

fun body(size: TextUnit = 16.sp, w: Int = 500, lh: Float = 1.5f) =
    TextStyle(fontFamily = Manrope, fontWeight = FontWeight(w), fontSize = size, lineHeight = (lh).em, lineHeightStyle = trim)

fun display(size: TextUnit, w: Int = 800, lh: Float = 1.15f) =
    TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(w), fontSize = size, lineHeight = lh.em, lineHeightStyle = trim)

fun mono(size: TextUnit, w: Int = 800, lh: Float = 1.3f) =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight(w), fontSize = size, lineHeight = lh.em, lineHeightStyle = trim)

/** Подпись-раздел: КАПС, разрядка */
fun caps(size: TextUnit = 13.sp) = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = size, letterSpacing = 0.08.em)

val GRADS = listOf(
    c(0xFFFF9A3D) to c(0xFFFF3D6E), c(0xFFA06BFF) to c(0xFF5B5FF0), c(0xFF2BD9F0) to c(0xFF3B82F6),
    c(0xFFE048FB) to c(0xFFFF4D8D), c(0xFF34D399) to c(0xFF0EA5E9), c(0xFFFBBF24) to c(0xFFF97316), c(0xFFF472B6) to c(0xFFA855F7)
)
fun coverGrad(id: String): Brush { val g = GRADS[id.sumOf { it.code } % GRADS.size]; return Brush.linearGradient(listOf(g.first, g.second)) }
