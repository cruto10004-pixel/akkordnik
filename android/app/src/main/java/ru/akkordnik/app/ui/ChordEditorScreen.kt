package ru.akkordnik.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.akkordnik.app.data.ChordDef
import ru.akkordnik.app.data.DEF_QUICK
import ru.akkordnik.app.music.barreInfo
import ru.akkordnik.app.music.bounds
import ru.akkordnik.app.music.canon
import ru.akkordnik.app.music.fmt
import ru.akkordnik.app.music.voicing
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ChordEditorScreen(app: AppState, d: ChordD) {
    val p = LocalPal.current
    val name = d.name
    val ex = remember { if (name.isNotEmpty()) voicing(name) else null }
    val frets = remember { mutableStateListOf<Int>().apply { addAll(ex?.frets ?: List(6) { -1 }) } }
    var barre by remember { mutableStateOf(ex != null && ex.barre > 0) }
    var photo by remember { mutableStateOf(ex?.photo ?: if (name.isNotEmpty()) ru.akkordnik.app.music.customFor(name)?.photo else null) }
    var start by remember { mutableIntStateOf(if (ex != null) bounds(ex.frets) else 1) }
    var cn by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    val nameFocus = remember { FocusRequester() }
    val pick = rememberPhotoPicker(app) { photo = it }
    val hasCustom = name.isNotEmpty() && app.store.db.chords.containsKey(canon(name))

    fun leave(inserted: String?) {
        app.pop()
        if (inserted != null) d.form?.let { it.addPending(inserted); app.toast.show("Аккорд ${fmt(inserted)} добавлен") }
    }

    fun save() {
        val n = cn.text.trim()
        if (n.isEmpty()) { nameFocus.requestFocus(); app.toast.show("Введите название"); return }
        if (frets.none { it >= 0 }) { app.toast.show("Отметьте хотя бы одну струну"); return }
        val k = canon(n)
        app.store.update { cur ->
            val q = if (k in cur.settings.quick) cur.settings.quick else cur.settings.quick + k
            cur.copy(chords = cur.chords + (k to ChordDef(frets.toList(), barre, photo)), settings = cur.settings.copy(quick = q))
        }
        if (d.form == null) app.toast.show("Аккорд ${fmt(k)} сохранён")
        leave(if (d.form != null) k else null)
    }

    fun reset() {
        app.dialogs.ask(
            "Сбросить аккорд?", "Ваша аппликатура и фото для «${fmt(name)}» будут удалены.",
            listOf(DBtn("Сбросить", "y", BtnKind.Danger, Ic.trash), DBtn("Отмена", null, BtnKind.Ghost))
        ) {
            if (it == "y") {
                val k = canon(name)
                app.store.update { cur ->
                    cur.copy(chords = cur.chords - k, settings = cur.settings.copy(quick = cur.settings.quick.filter { q -> q != k || q in DEF_QUICK }))
                }
                app.toast.show("Аккорд сброшен")
                leave(null)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (name.isNotEmpty()) "Аккорд ${fmt(name)}" else "Новый аккорд", "Нажимайте на гриф, чтобы ставить пальцы", onBack = { leave(null) })
        Column(
            Modifier.fillMaxWidth().weight(1f).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(Modifier.rise(0)) {
                FieldLabel("Название")
                Field(cn, { cn = it }, Modifier.focusRequester(nameFocus), placeholder = "Например, Cadd9 или Am/G", style = mono(16.sp, 800, 1.4f),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false))
            }
            Column(Modifier.rise(1)) {
                FieldLabel("Аппликатура")
                Card {
                    Column(Modifier.padding(12.dp)) {
                        EditorNeck(frets, start, barre)
                        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T("Начальный лад", body(16.sp, 700, 1.35f))
                                T("Сдвинуть схему по грифу", body(14.sp, 500, 1.4f), p.muted)
                            }
                            Stepper("$start", { if (start > 1) start-- }, { if (start < 15) start++ }, "Ниже", "Выше")
                        }
                        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T("Баррэ", body(16.sp, 700, 1.35f))
                                T("Зажать струны на самом низком ладу", body(14.sp, 500, 1.4f), p.muted)
                            }
                            Toggle(barre) { barre = it }
                        }
                    }
                }
                Hint("Над грифом: ○ — открытая струна, × — не играть. Нажмите ещё раз, чтобы переключить.")
            }
            Column(Modifier.rise(2)) {
                FieldLabel("Фото — как ставить")
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(shape).background(p.surface).dashed(p.line.copy(alpha = .3f), 18.dp, 1.dp).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    AnimatedContent(photo, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(120)) }, label = "ph") { ph ->
                        if (ph != null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val bmp by rememberPhoto(ph)
                            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                bmp?.let { Image(it, "Фото аккорда", Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit) } ?: Spinner()
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Заменить", pick, Modifier.weight(1f), Ic.camera, BtnKind.Ghost, 46.dp, fontSize = 15, padH = 12.dp)
                                Btn("Убрать", { photo = null }, Modifier.weight(1f), Ic.trash, BtnKind.Danger, 46.dp, fontSize = 15, padH = 12.dp)
                            }
                        } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Btn("Добавить фото", pick, icon = Ic.camera, kind = BtnKind.Ghost)
                            T("Фото руки на грифе — чтобы потом быстро вспомнить", body(14.sp, 500, 1.4f), p.muted, align = TextAlign.Center)
                        }
                    }
                }
            }
            Row(Modifier.rise(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Сохранить аккорд", { save() }, Modifier.weight(1f), Ic.check)
                if (hasCustom) Btn("Сбросить", { reset() }, Modifier.weight(1f), Ic.trash, BtnKind.Danger)
            }
        }
    }
}

/** Интерактивный гриф редактора: тап по ладу ставит палец, тап над грифом — ○ / ×. */
@Composable
private fun EditorNeck(frets: MutableList<Int>, start: Int, barre: Boolean) {
    val p = LocalPal.current
    val tm = rememberTextMeasurer()
    val hf = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pops = remember { List(6) { Animatable(1f) } }
    val x0 = 50f; val y0 = 56f; val sw = 40f; val fh = 44f
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(300f / 318f).pointerInput(start) {
                detectTapGestures { pos ->
                    val k = size.width / 300f
                    val x = pos.x / k; val y = pos.y / k
                    val s = ((x - x0) / sw).roundToInt()
                    if (s !in 0..5 || kotlin.math.abs(x - (x0 + s * sw)) > 20f) return@detectTapGestures
                    if (y in (y0 - 46)..(y0 - 4)) {
                        frets[s] = if (frets[s] == 0) -1 else 0
                    } else if (y > y0 - 4 && y < y0 + 5 * fh) {
                        val r = floor((y - y0) / fh).toInt().coerceIn(0, 4)
                        val fr = start + r
                        frets[s] = if (frets[s] == fr) 0 else fr
                        if (frets[s] > 0) scope.launch { pops[s].snapTo(.4f); pops[s].animateTo(1f, spring(dampingRatio = .45f, stiffness = 500f)) }
                    } else return@detectTapGestures
                    hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        ) {
            val k = size.width / 300f
            scale(k, k, pivot = Offset.Zero) {
                val tx = p.text
                val grad = { a: Offset, b: Offset -> Brush.linearGradient(listOf(p.a1, p.a2), a, b) }
                fun label(t: String, x: Float, base: Float, sz: Float, c: androidx.compose.ui.graphics.Color, anchor: Int = 0) {
                    val r = tm.measure(t, TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = sz.toSp(), color = c))
                    val left = if (anchor == 1) x - r.size.width else x - r.size.width / 2f
                    drawText(r, topLeft = Offset(left, base - r.firstBaseline))
                }
                if (start == 1) drawRoundRect(tx, Offset(x0 - 2, y0 - 7), Size(sw * 5 + 4, 7f), CornerRadius(2f))
                else label("$start", x0 - 14, y0 + fh / 2 + 5, 14f, p.muted, 1)
                for (i in 0..5) drawLine(tx.copy(alpha = .25f), Offset(x0, y0 + i * fh), Offset(x0 + sw * 5, y0 + i * fh), 2f)
                for (s in 0 until 6) drawLine(tx.copy(alpha = .5f), Offset(x0 + s * sw, y0), Offset(x0 + s * sw, y0 + 5 * fh), 2.8f - s * .3f)
                val (bb, bf) = barreInfo(frets, barre)
                if (bb > 0 && bb >= start && bb < start + 5) {
                    val y = y0 + (bb - start + .5f) * fh
                    val bx = x0 + bf * sw - 14; val bw = (5 - bf) * sw + 28
                    drawRoundRect(grad(Offset(bx, y - 14), Offset(bx + bw, y + 14)), Offset(bx, y - 14), Size(bw, 28f), CornerRadius(14f), alpha = .55f)
                }
                frets.forEachIndexed { s, f ->
                    val x = x0 + s * sw
                    when {
                        f < 0 -> label("×", x, y0 - 20, 20f, p.muted)
                        f == 0 -> drawCircle(tx, 8f, Offset(x, y0 - 26), style = Stroke(2.4f))
                        else -> drawCircle(p.muted, 3f, Offset(x, y0 - 26))
                    }
                    if (f >= start && f < start + 5) {
                        val y = y0 + (f - start + .5f) * fh
                        val r = 14f * pops[s].value
                        drawCircle(grad(Offset(x - 14, y - 14), Offset(x + 14, y + 14)), r, Offset(x, y))
                    }
                }
                listOf("E", "A", "D", "G", "B", "e").forEachIndexed { s, n -> label(n, x0 + s * sw, y0 + 5 * fh + 26, 13f, p.muted) }
            }
        }
    }
}
