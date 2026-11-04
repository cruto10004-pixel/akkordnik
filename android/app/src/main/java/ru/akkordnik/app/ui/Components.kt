@file:OptIn(ExperimentalTextApi::class)

package ru.akkordnik.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val EaseApp = CubicBezierEasing(.2f, .7f, .2f, 1f)
fun <T> springy() = spring<T>(dampingRatio = .55f, stiffness = 520f)

/** Нажатие с «пружинкой» вместо риппла. */
fun Modifier.tap(
    enabled: Boolean = true, scale: Float = .94f, haptic: Boolean = false, role: Role = Role.Button, onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val motion = LocalMotion.current
    val hf = LocalHapticFeedback.current
    val s by animateFloatAsState(if (pressed && motion) scale else 1f, spring(dampingRatio = .5f, stiffness = 700f), label = "tap")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, indication = null, enabled = enabled, role = role) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Появление снизу с задержкой (каскад). */
fun Modifier.rise(index: Int, active: Boolean = true, step: Int = 45, base: Int = 80): Modifier = composed {
    val motion = LocalMotion.current
    val start = LocalScreenStart.current
    val anim = remember { Animatable(if (active && motion && entering(start)) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (anim.value < 1f) { delay((index * step + base).toLong()); anim.animateTo(1f, tween(480, easing = EaseApp)) }
    }
    val d = LocalDensity.current
    graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * with(d) { 16.dp.toPx() } }
}

private fun entering(start: Long) = start == 0L || android.os.SystemClock.uptimeMillis() - start < 900

/** «Поп»: из 0.6 с лёгким перелётом. */
fun Modifier.pop(delayMs: Int = 0, active: Boolean = true, always: Boolean = false): Modifier = composed {
    val motion = LocalMotion.current
    val start = LocalScreenStart.current
    val anim = remember { Animatable(if (active && motion && (always || entering(start))) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (anim.value < 1f) { delay(delayMs.toLong()); anim.animateTo(1f, spring(dampingRatio = .45f, stiffness = 420f)) }
    }
    graphicsLayer {
        val v = anim.value
        val sc = .6f + .4f * v
        scaleX = sc; scaleY = sc; alpha = v.coerceIn(0f, 1f)
    }
}

@Composable
fun T(
    text: String, style: TextStyle, color: Color = LocalPal.current.text, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null, softWrap: Boolean = true,
) = Text(
    text, modifier, color = color, style = style, maxLines = maxLines, overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
    textAlign = align, softWrap = softWrap
)

enum class BtnKind { Grad, Ghost, Danger }

@Composable
fun Btn(
    text: String?, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: BtnKind = BtnKind.Grad,
    height: Dp = 50.dp, enabled: Boolean = true, fontSize: Int = 16, padH: Dp = 20.dp, shadow: Boolean = true,
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(14.dp)
    val fg = when (kind) { BtnKind.Grad -> p.onGrad; BtnKind.Ghost -> p.text; BtnKind.Danger -> p.danger }
    var m = modifier.tap(enabled) { onClick() }.heightIn(min = height)
    if (kind == BtnKind.Grad && shadow && enabled) m = m.shadow(14.dp, shape, ambientColor = p.a2, spotColor = p.a2)
    m = m.clip(shape)
    m = when (kind) {
        BtnKind.Grad -> m.background(p.grad)
        BtnKind.Ghost -> m.background(p.surface).border(1.dp, p.line, shape)
        BtnKind.Danger -> m.border(1.dp, p.danger.copy(alpha = .45f), shape)
    }
    Row(
        m.graphicsLayer { alpha = if (enabled) 1f else .5f }.padding(horizontal = padH),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = fg)
        if (icon != null && text != null) Spacer(Modifier.width(8.dp))
        if (text != null) T(text, body(fontSize.sp, 800, 1.2f), fg, maxLines = 1)
    }
}

@Composable
fun IconBtn(
    icon: ImageVector, desc: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp,
    plain: Boolean = false, on: Boolean = false, iconSize: Dp = 22.dp, tint: Color? = null,
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    var m = modifier.tap(onClick = onClick).size(size).clip(shape)
    m = when {
        on -> m.background(p.grad)
        plain -> m
        else -> m.background(p.surface).border(1.dp, p.line, shape)
    }
    Box(m, contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(iconSize), tint = tint ?: if (on) p.onGrad else p.text)
    }
}

@Composable
fun TextIconBtn(label: String, desc: String, fontSize: Int, onClick: () -> Unit, size: Dp = 44.dp) {
    val p = LocalPal.current
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        T(label, body(fontSize.sp, 800, 1f), p.text)
    }
}

@Composable
fun ChordChip(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 46.dp, fontSize: Int = 16,
    picked: Boolean = false, selected: Boolean = false, trailing: String? = null,
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    val bg = when { selected -> null; picked -> p.accent.copy(alpha = .16f).compositeOver(p.surface); else -> p.surface }
    var m = modifier.tap(haptic = true, onClick = onClick).height(height).widthIn(min = if (height < 44.dp) 50.dp else 58.dp).clip(shape)
    m = if (bg == null) m.background(p.grad) else m.background(bg).border(1.dp, if (picked) p.accent else p.line, shape)
    Row(m.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        T(text, mono(fontSize.sp, 800, 1.1f), if (selected) p.onGrad else p.accent, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            T(trailing, body(18.sp, 700, 1f), (if (selected) p.onGrad else p.text).copy(alpha = .75f))
        }
    }
}

fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun AddChip(text: String, onClick: () -> Unit, height: Dp = 42.dp) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.tap(onClick = onClick).height(height).clip(shape).dashed(p.line.copy(alpha = (p.line.alpha * 2.2f).coerceAtMost(1f)), 12.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.plus, null, Modifier.size(18.dp), tint = p.text)
        Spacer(Modifier.width(6.dp))
        T(text, body(15.sp, 700, 1f))
    }
}

fun Modifier.dashed(color: Color, radius: Dp, width: Dp = 1.5.dp, dash: Float = 9f, gap: Float = 7f) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density, gap * density)))
    )
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = .55f, stiffness = 500f), label = "tg")
    Box(
        Modifier.tap(haptic = true, role = Role.Switch) { onChange(!on) }.size(52.dp, 32.dp).clip(CircleShape).background(p.raised)
            .then(if (on) Modifier.background(p.grad) else Modifier)
    ) {
        Box(Modifier.offset(x = 4.dp + x, y = 4.dp).size(24.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Сегменты с плавающей «таблеткой». */
@Composable
fun Segmented(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    height: Dp = 40.dp, fontSize: Int = 14, minItem: Dp = 0.dp, radius: Dp = 10.dp,
) {
    val p = LocalPal.current
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(radius + 4.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(radius + 4.dp)).padding(4.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = .62f, stiffness = 420f), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(height).clip(RoundedCornerShape(radius)).background(p.grad))
        Row {
            options.forEachIndexed { i, o ->
                val c by androidx.compose.animation.animateColorAsState(if (i == selected) p.onGrad else p.muted, tween(250), label = "segc")
                Box(Modifier.width(w).height(height).clip(RoundedCornerShape(radius)).tap(scale = .96f, haptic = true) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    T(o, body(fontSize.sp, 800, 1.1f), c, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusDesc: String = "Меньше", plusDesc: String = "Больше") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconBtn(Ic.minus, minusDesc, onMinus)
        Bump(value) { T(it, body(16.sp, 800, 1.2f), modifier = Modifier.widthIn(min = 40.dp), align = TextAlign.Center) }
        IconBtn(Ic.plus, plusDesc, onPlus)
    }
}

/** Лёгкий «подпрыг» при смене значения. */
@Composable
fun <K> Bump(key: K, content: @Composable (K) -> Unit) {
    val motion = LocalMotion.current
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        if (motion) { s.snapTo(1.3f); s.animateTo(1f, spring(dampingRatio = .4f, stiffness = 500f)) }
    }
    Box(Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }) { content(key) }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape)) { content() }
}

@Composable
fun SettingRow(title: String, sub: String?, first: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(title, body(16.sp, 700, 1.35f))
                if (sub != null) T(sub, body(14.sp, 500, 1.4f), p.muted)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun Caps(text: String, color: Color = LocalPal.current.muted, modifier: Modifier = Modifier, size: Int = 13) =
    T(text.uppercase(), caps(size.sp), color, modifier)

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) = T(text, body(14.sp, 800, 1.3f), modifier = modifier.padding(bottom = 8.dp))

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) = T(text, body(14.sp, 500, 1.45f), LocalPal.current.muted, modifier.padding(top = 8.dp))

/** Поле ввода в стиле приложения (рамка, подсветка при фокусе). */
@Composable
fun Field(
    value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    singleLine: Boolean = true, minHeight: Dp = 52.dp, style: TextStyle = body(16.sp, 500, 1.45f),
    keyboard: KeyboardOptions = KeyboardOptions.Default, actions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "ring")
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value, onChange, modifier
            .fillMaxWidth()
            .drawBehind {
                if (ring > 0f) {
                    val o = 4.dp.toPx() * ring
                    drawRoundRect(p.accent.copy(alpha = .18f * ring), Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(14.dp.toPx() + o))
                }
            },
        textStyle = style.copy(color = p.text), singleLine = singleLine, cursorBrush = SolidColor(p.accent),
        keyboardOptions = keyboard, keyboardActions = actions, interactionSource = src,
        decorationBox = { inner ->
            Row(
                Modifier.heightIn(min = minHeight).clip(shape).background(p.surface)
                    .border(1.dp, if (focused) p.accent else p.line, shape),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leading != null) leading()
                Box(Modifier.weight(1f).padding(padding)) {
                    if (value.text.isEmpty()) T(placeholder, style, p.muted.copy(alpha = .5f), maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                    inner()
                }
                if (trailing != null) trailing()
            }
        }
    )
}

@Composable
fun TopBar(title: String, sub: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().background(p.bg.copy(alpha = .94f))) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBtn(Ic.back, "Назад", onBack)
            Column(Modifier.weight(1f)) {
                T(title, display(16.sp, 700, 1.25f), maxLines = 1)
                if (!sub.isNullOrEmpty()) T(sub, body(13.sp, 500, 1.35f), p.muted, maxLines = 1)
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

@Composable
fun GradText(text: String, style: TextStyle, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    val p = LocalPal.current
    Text(text, modifier, style = style.copy(brush = Brush.linearGradient(listOf(p.a1, p.a2))), textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Spinner(size: Dp = 18.dp) {
    val p = LocalPal.current
    val inf = rememberInfiniteTransition(label = "spin")
    val a by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size)) {
        val w = 2.5.dp.toPx()
        drawArc(p.line, 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w))
        drawArc(p.accent, a - 90f, 90f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Мягкие цветные пятна на фоне (медленно плавают, если включены анимации). */
@Composable
fun Glow(modifier: Modifier = Modifier) {
    if (LocalMotion.current) {
        val inf = rememberInfiniteTransition(label = "glow")
        val t1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g1")
        val t2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g2")
        GlowCanvas(modifier, { t1 }, { t2 })
    } else GlowCanvas(modifier, { 0f }, { 0f })
}

@Composable
private fun GlowCanvas(modifier: Modifier, t1: () -> Float, t2: () -> Float) {
    val p = LocalPal.current
    Canvas(modifier.fillMaxSize()) {
        val vmax = maxOf(size.width, size.height) / 100f
        val a = t1()
        val b = t2()
        val r1 = 35f * vmax * (1f + .1f * a)
        val c1 = Offset(13f * vmax + 12f * vmax * a, -3f * vmax + 10f * vmax * a)
        drawCircle(Brush.radialGradient(listOf(p.g1, Color.Transparent), c1, r1), r1, c1)
        val r2 = 35f * vmax * (1f + .15f * b)
        val c2 = Offset(size.width - 7f * vmax - 10f * vmax * b, size.height + 9f * vmax - 12f * vmax * b)
        drawCircle(Brush.radialGradient(listOf(p.g2, Color.Transparent), c2, r2), r2, c2)
    }
}

// ---------- тост ----------
class ToastState { var msg by mutableStateOf<Pair<String, Long>?>(null); fun show(m: String) { msg = m to System.nanoTime() } }

@Composable
fun ToastHost(state: ToastState) {
    val p = LocalPal.current
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(state.msg) {
        val m = state.msg ?: return@LaunchedEffect
        text = m.first; visible = true
        delay(2200); visible = false
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(200)) + slideInVertically(springy()) { -it } + scaleIn(springy(), .9f),
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { -it / 2 },
        ) {
            Box(Modifier.shadow(16.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(p.text).padding(horizontal = 18.dp, vertical = 11.dp)) {
                T(text, body(14.sp, 800, 1.3f), p.bg, align = TextAlign.Center)
            }
        }
    }
}

/** Выход за боковые отступы родителя (прокрутка от края до края). */
fun Modifier.bleed(h: Dp) = this.then(Modifier.layout { m, c ->
    val e = h.roundToPx()
    val pl = m.measure(c.copy(minWidth = c.minWidth + 2 * e, maxWidth = c.maxWidth + 2 * e))
    layout(c.maxWidth, pl.height) { pl.place(-e, 0) }
})
