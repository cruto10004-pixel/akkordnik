package ru.akkordnik.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import ru.akkordnik.app.music.Voicing
import ru.akkordnik.app.music.bounds
import ru.akkordnik.app.music.fingers

/** Анимация «выскакивания» точек: время с начала показа, мс. */
@Composable
fun rememberDotClock(key: Any?, animate: Boolean): Animatable<Float, *> {
    val motion = LocalMotion.current
    val a = remember(key) { Animatable(if (animate && motion) 0f else 5000f) }
    LaunchedEffect(key) { if (a.value < 5000f) a.animateTo(1600f, tween(1600, easing = LinearEasing)) }
    return a
}

private fun popScale(clock: Float, i: Int, step: Int, base: Int): Float {
    val t = ((clock - (i * step + base)) / 450f).coerceIn(0f, 1f)
    return t
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, baseline: Float, size: Float, color: Color, w: Int = 800, anchor: Int = 0) {
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(w), fontSize = size.toSp(), color = color)
    val r = tm.measure(text, style)
    val left = when (anchor) { 0 -> x - r.size.width / 2f; 1 -> x - r.size.width; else -> x }
    drawText(r, topLeft = Offset(left, baseline - r.firstBaseline))
}

/** Классическая схема аккорда (как в песенниках). */
@Composable
fun Diagram(v: Voicing, modifier: Modifier = Modifier, animate: Boolean = true) {
    val p = LocalPal.current
    val tm = rememberTextMeasurer()
    val clock = rememberDotClock(v, animate)
    Canvas(modifier.aspectRatio(230f / 280f)) {
        val k = size.width / 230f
        scale(k, k, pivot = Offset.Zero) { drawDiagram(v, p, tm, clock.value) }
    }
}

fun DrawScope.drawDiagram(v: Voicing, p: Pal, tm: TextMeasurer, clock: Float) {
    val x0 = 42f; val y0 = 50f; val sw = 29f; val fh = 40f; val n = 5
    val start = bounds(v.frets)
    val f = fingers(v)
    val tx = p.text
    if (start == 1) drawRoundRect(tx, Offset(x0 - 2, y0 - 7), Size(sw * 5 + 4, 7f), CornerRadius(2f))
    else label(tm, "$start лад", x0 - 12, y0 + fh / 2 + 5, 14f, tx.copy(alpha = .7f), anchor = 1)
    for (i in 0..n) drawLine(tx.copy(alpha = .28f), Offset(x0, y0 + i * fh), Offset(x0 + sw * 5, y0 + i * fh), 2f)
    for (s in 0 until 6) drawLine(tx.copy(alpha = .55f), Offset(x0 + s * sw, y0), Offset(x0 + s * sw, y0 + n * fh), 2.8f - s * .32f)
    var d = 0
    fun dot(cx: Float, cy: Float, draw: DrawScope.() -> Unit) {
        val t = popScale(clock, d++, 70, 120)
        if (t <= 0f) return
        val sc = .6f + .4f * EaseOutBack.transform(t)
        withTransform({ scale(sc, sc, Offset(cx, cy)) }) { draw() }
    }
    if (v.barre > 0 && v.barre >= start) {
        val y = y0 + (v.barre - start + .5f) * fh
        val bx = x0 + v.barreFrom * sw - 12
        val bw = (5 - v.barreFrom) * sw + 24
        dot(bx + bw / 2, y) {
            drawRoundRect(Brush.linearGradient(listOf(p.a1, p.a2), Offset(bx, y - 12), Offset(bx + bw, y + 12)), Offset(bx, y - 12), Size(bw, 24f), CornerRadius(12f))
            label(tm, "1", x0 + v.barreFrom * sw, y + 5, 13f, p.onGrad)
        }
    }
    v.frets.forEachIndexed { s, fr ->
        val x = x0 + s * sw
        when {
            fr < 0 -> label(tm, "×", x, y0 - 16, 17f, tx.copy(alpha = .6f))
            fr == 0 -> drawCircle(tx, 6.5f, Offset(x, y0 - 21), style = Stroke(2.2f))
            !(v.barre == fr && s >= v.barreFrom) -> {
                val y = y0 + (fr - start + .5f) * fh
                dot(x, y) {
                    drawCircle(Brush.linearGradient(listOf(p.a1, p.a2), Offset(x - 12, y - 12), Offset(x + 12, y + 12)), 12f, Offset(x, y))
                    if (f != null) label(tm, "${f[s]}", x, y + 5, 13f, p.onGrad)
                }
            }
        }
    }
    listOf("E", "A", "D", "G", "B", "e").forEachIndexed { s, nm -> label(tm, nm, x0 + s * sw, y0 + n * fh + 26, 13f, tx.copy(alpha = .6f), 700) }
}

private val wood = listOf(Color(0xFF3B2416), Color(0xFF5A3520), Color(0xFF2E1B10))
private val fretMetal = listOf(Color(0xFF8A8F96), Color(0xFFF1F3F5), Color(0xFF7C828A))
private val strCol = listOf(Color(0xFFFFF6DD), Color(0xFFA7998A))
private val tipCol = listOf(Color(0xFFFFD9C4), Color(0xFFF0AE8E), Color(0xFFC47B5E))
private val labelCol = Color(255, 240, 220, 140)

/** Гриф «как видишь его на гитаре»: тонкая струна сверху. */
@Composable
fun Neck(v: Voicing, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val tm = rememberTextMeasurer()
    val clock = rememberDotClock(v, true)
    Canvas(modifier.aspectRatio(440f / 230f)) {
        val k = size.width / 440f
        scale(k, k, pivot = Offset.Zero) { drawNeck(v, tm, clock.value) }
    }
}

private fun DrawScope.drawNeck(v: Voicing, tm: TextMeasurer, clock: Float) {
    val start = bounds(v.frets)
    val f = fingers(v)
    val nf = 5; val w = 440f; val h = 230f; val x0 = 66f; val fw = (w - x0 - 14) / nf; val y0 = 30f; val sg = 34f
    val top = y0 - 14; val bh = sg * 5 + 28
    drawRoundRect(Color(0xFF1A120C), Offset.Zero, Size(w, h), CornerRadius(14f))
    drawRect(Brush.verticalGradient(wood, top, top + bh), Offset(x0, top), Size(w - x0 - 6, bh))
    clipRect(x0, top, w - 6, top + bh) {
        var yy = top
        while (yy < top + bh) {
            var xx = x0
            while (xx < w) {
                val p1 = Path().apply { moveTo(xx, yy + 7); quadraticTo(xx + 30, yy + 3, xx + 60, yy + 7); quadraticTo(xx + 90, yy + 11, xx + 120, yy + 7) }
                drawPath(p1, Color(0, 0, 0, 46), style = Stroke(1.2f))
                val p2 = Path().apply { moveTo(xx, yy + 12); quadraticTo(xx + 40, yy + 9, xx + 80, yy + 12); quadraticTo(xx + 120, yy + 15, xx + 160, yy + 12) }
                drawPath(p2, Color(255, 220, 180, 15), style = Stroke(1f))
                xx += 120
            }
            yy += 14
        }
    }
    if (start == 1) drawRect(Color(0xFFEDE3CC), Offset(x0 - 10, top), Size(10f, bh))
    for (i in 1..nf) { val x = x0 + i * fw; drawRect(Brush.horizontalGradient(fretMetal, x - 2.5f, x + 2.5f), Offset(x - 2.5f, top), Size(5f, bh)) }
    for (i in 0 until nf) {
        val fr = start + i; val cx = x0 + (i + .5f) * fw; val cy = y0 + sg * 2.5f
        val inlay = Color(0xFFE8DCC4).copy(alpha = .85f)
        if (fr in listOf(3, 5, 7, 9, 15, 17)) drawCircle(inlay, 7f, Offset(cx, cy))
        if (fr == 12) { drawCircle(inlay, 6f, Offset(cx, cy - sg)); drawCircle(inlay, 6f, Offset(cx, cy + sg)) }
        label(tm, "$fr", cx, h - 8, 12f, Color(255, 240, 220, 140), 700)
    }
    for (r in 0 until 6) {
        val s = 5 - r; val y = y0 + r * sg; val sw = 1.2f + s * .45f
        drawRect(Brush.verticalGradient(strCol, y - sw / 2, y + sw / 2), Offset(x0 - 10, y - sw / 2), Size(w - x0 + 4, sw))
    }
    var d = 0
    fun tip(cx: Float, cy: Float, rw: Float, rh: Float, round: Boolean, txt: String?) {
        val t = popScale(clock, d++, 80, 150)
        if (t <= 0f) return
        val sc = .6f + .4f * EaseOutBack.transform(t)
        withTransform({ scale(sc, sc, Offset(cx, cy)) }) {
            // мягкая тень
            for (k in 3 downTo 1) {
                val e = k * 1.6f
                if (round) drawRoundRect(Color(0, 0, 0, 30), Offset(cx - rw - e / 2, cy - rh + 3 - e / 2), Size(2 * rw + e, 2 * rh + e), CornerRadius(rw + e / 2))
                else drawOval(Color(0, 0, 0, 30), Offset(cx - rw - e / 2, cy - rh + 3 - e / 2), Size(2 * rw + e, 2 * rh + e))
            }
            val br = Brush.radialGradient(listOf(tipCol[0], tipCol[1], tipCol[2]), Offset(cx - rw + .35f * 2 * rw, cy - rh + .3f * 2 * rh), .8f * maxOf(2 * rw, 2 * rh) * .75f)
            if (round) drawRoundRect(br, Offset(cx - rw, cy - rh), Size(2 * rw, 2 * rh), CornerRadius(rw))
            else drawOval(br, Offset(cx - rw, cy - rh), Size(2 * rw, 2 * rh))
            if (txt != null) label(tm, txt, cx, cy + 5, 14f, Color(0xFF5B2E1C))
        }
    }
    if (v.barre > 0 && v.barre >= start) {
        val x = x0 + (v.barre - start + .62f) * fw
        val t0 = y0 - 12; val b0 = y0 + (5 - v.barreFrom) * sg + 12
        tip(x, (t0 + b0) / 2, 13f, (b0 - t0) / 2, true, "1")
    }
    v.frets.forEachIndexed { s, fr ->
        val y = y0 + (5 - s) * sg
        when {
            fr < 0 -> label(tm, "×", 44f, y + 6, 18f, Color(0xFFFF7B7B))
            fr == 0 -> drawCircle(Color(0xFF7BE0A6), 7f, Offset(44f, y), style = Stroke(2.4f))
            !(v.barre == fr && s >= v.barreFrom) -> tip(x0 + (fr - start + .62f) * fw, y, 15f, 13f, false, f?.let { "${it[s]}" })
        }
    }
    listOf("e", "B", "G", "D", "A", "E").forEachIndexed { r, nm -> label(tm, nm, 16f, y0 + r * sg + 5, 13f, Color(255, 240, 220, 153), 700) }
}
