package ru.akkordnik.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
import ru.akkordnik.app.music.Lyric
import ru.akkordnik.app.music.fmt
import ru.akkordnik.app.music.hostOf
import ru.akkordnik.app.music.layoutLyrics
import ru.akkordnik.app.music.simpleNotes
import ru.akkordnik.app.music.songChords
import ru.akkordnik.app.music.transpose

val SPEEDS = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongScreen(app: AppState, d: SongD) {
    val p = LocalPal.current
    val db = app.store.db
    val s = db.songs.firstOrNull { it.id == d.id }
    if (s == null) { LaunchedEffect(Unit) { app.pop() }; return }
    if (d.size < 0) d.size = db.settings.size
    val chords = remember(s, d.tr, db.chords, db.settings.quick) { songChords(s, d.tr) }
    val lines = remember(s.text, db.chords, db.settings.quick) { layoutLyrics(s.text) }
    val notes = if (s.mode == "simple") simpleNotes(s).trim() else ""
    var sheet by remember { mutableStateOf<String?>(null) }
    val size by animateFloatAsState(d.size.toFloat(), tween(if (LocalMotion.current) 250 else 0, easing = EaseApp), label = "size")
    var trDir by remember { mutableStateOf(1) }

    // экран не гаснет, пока открыта песня; полноэкранный режим прячет системные панели
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val activity = view.context as? Activity
    LaunchedEffect(d.stage) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        if (d.stage) { c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; c.hide(WindowInsetsCompat.Type.systemBars()) }
        else c.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose { activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    BackHandler(enabled = d.stage) { d.stage = false }

    // автопрокрутка
    val density = LocalDensity.current
    LaunchedEffect(d.scrolling, d.speed) {
        if (!d.scrolling) return@LaunchedEffect
        var last = withFrameNanos { it }
        var acc = 0f
        while (isActive) {
            val t = withFrameNanos { it }
            acc += with(density) { 20.dp.toPx() } * SPEEDS[d.speed] * ((t - last) / 1e9f)
            last = t
            if (acc >= 1f) {
                val step = acc.toInt().toFloat()
                acc -= step
                try { d.list.scrollBy(step) } catch (e: CancellationException) { if (!isActive) throw e }
            }
            if (!d.list.canScrollForward) { delay(400); if (!d.list.canScrollForward) { d.scrolling = false; break } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(!d.stage, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                TopBar(s.title, s.artist, onBack = { app.pop() }) {
                    IconBtn(Ic.edit, "Редактировать", { app.editSong(s) })
                }
            }
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f), state = d.list,
                contentPadding = PaddingValues(
                    top = if (d.stage) statusTop + 28.dp else 0.dp,
                    bottom = 170.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            ) {
                if (!d.stage) {
                    item(key = "capo") { CapoCard(s.capo, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).rise(0)) }
                    item(key = "lbl") {
                        Text(
                            buildAnnotatedString {
                                append("АККОРДЫ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.sp)) { append(" — нажмите, чтобы увидеть, как ставить") }
                            },
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp).rise(1), style = caps(13.sp), color = p.muted
                        )
                    }
                    item(key = "chips") {
                        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chords.forEachIndexed { i, c ->
                                Box(Modifier.pop(150 + i * 40)) { RollChip(c, trDir) { sheet = c } }
                            }
                            if (chords.isEmpty()) T("В песне пока нет аккордов", body(14.sp, 500, 1.4f), p.muted)
                        }
                    }
                    if (notes.isNotEmpty()) item(key = "notes") {
                        val shape = RoundedCornerShape(16.dp)
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(horizontal = 16.dp, vertical = 12.dp).rise(2)) {
                            Caps("Пометки", p.a2, size = 12)
                            Spacer(Modifier.height(4.dp))
                            T(notes, body(16.sp, 500, 1.5f))
                        }
                    }
                    if (s.source.isNotEmpty()) item(key = "src") {
                        T("Источник: ${hostOf(s.source)}", body(13.sp, 500, 1.4f), p.muted, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
                    }
                    item(key = "gap") { Spacer(Modifier.height(12.dp)) }
                }
                itemsIndexed(lines, key = { i, _ -> "l$i" }) { _, l ->
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        when (l) {
                            is Lyric.Section -> T(l.label.uppercase(), caps(13.sp).copy(letterSpacing = 0.09.em), p.sec, Modifier.padding(top = 24.dp, bottom = 6.dp))
                            is Lyric.Blank -> Spacer(Modifier.height(with(density) { (size * 1.5f).sp.toDp() } + 9.dp))
                            is Lyric.Line -> LyricLine(l, size, d.tr) { sheet = it }
                        }
                    }
                }
                if (lines.isEmpty()) item { T("Текст песни пока не добавлен", body(16.sp, 500, 1.5f), p.muted, Modifier.padding(20.dp)) }
            }
        }
        Tools(d, Modifier.align(Alignment.BottomCenter), onTr = { n ->
            trDir = n
            var t = ((d.tr + n) % 12 + 12) % 12
            if (t > 6) t -= 12
            d.tr = t
        })
    }
    sheet?.let { name ->
        ChordSheet(app, name, chords, onClose = { sheet = null }, onSwitch = { sheet = it }, onEdit = { n -> sheet = null; app.push(ChordD(n, null)) })
    }
}

@Composable
private fun RollChip(c: String, dir: Int, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.tap(haptic = true, onClick = onClick).height(46.dp).widthIn(min = 58.dp).clip(shape).background(p.surface).border(1.dp, p.line, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(c, transitionSpec = {
            (slideInVertically(tween(260, easing = EaseApp)) { h -> h * dir } + fadeIn(tween(200))) togetherWith
                (slideOutVertically(tween(260, easing = EaseApp)) { h -> -h * dir } + fadeOut(tween(160))) using SizeTransform(clip = true)
        }, label = "roll") { name -> T(fmt(name), mono(16.sp, 800, 1.1f), p.accent, maxLines = 1) }
    }
}

@Composable
private fun CapoCard(capo: Int, modifier: Modifier) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(18.dp)
    val motion = LocalMotion.current
    val shine = remember { Animatable(0f) }
    LaunchedEffect(capo) { if (capo > 0 && motion) { shine.snapTo(0f); delay(500); shine.animateTo(1f, tween(2800, easing = EaseApp)) } }
    var m = modifier.fillMaxWidth().clip(shape)
    m = if (capo > 0) m.background(p.grad).drawWithContent {
        drawContent()
        val v = shine.value
        if (v in 0.001f..0.999f) {
            val x = -size.width + 2 * size.width * v
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .28f), Color.Transparent), Offset(x + size.width * .3f, 0f), Offset(x + size.width * .7f, size.height * .4f)))
        }
    } else m.background(p.surface).border(1.dp, p.line, shape)
    val fg = if (capo > 0) p.onGrad else p.text
    Row(m.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        T(if (capo > 0) "$capo" else "—", display(34.sp, 800, 1f), if (capo > 0) fg else p.muted, Modifier.widthIn(min = 40.dp), align = TextAlign.Center)
        Column {
            T(if (capo > 0) "Каподастр на $capo ладу" else "Без каподастра", body(17.sp, 800, 1.3f), fg)
            T(if (capo > 0) "Аккорды указаны относительно капо" else "Играйте в открытой позиции", body(14.sp, 600, 1.4f), fg.copy(alpha = .8f))
        }
    }
}

/** Строка песни: аккорды над словами, длинные строки переносятся с отступом. */
@Composable
private fun LyricLine(line: Lyric.Line, size: Float, tr: Int, onChord: (String) -> Unit) {
    val p = LocalPal.current
    val word = body(size.sp, 500, 1.45f)
    val chordStyle = mono((size * .84f).sp, 800, 1.3f)
    // плоский список детей: для каждого сегмента — [аккорд], слово
    val kinds = remember(line) {
        buildList { line.groups.forEachIndexed { gi, g -> g.forEach { s -> if (s.c != null) add(gi to true); add(gi to false) } } }
    }
    Layout(
        content = {
            line.groups.forEach { g ->
                g.forEach { seg ->
                    if (seg.c != null) {
                        val c = transpose(seg.c, tr)
                        T(fmt(c), chordStyle, p.accent, Modifier.tap(scale = .86f, haptic = true) { onChord(c) }, maxLines = 1, softWrap = false)
                    }
                    T(seg.w.replace(' ', '\u00A0'), word, p.text, maxLines = 1, softWrap = false)
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = if (line.hasChords) 4.dp else 0.dp, bottom = 9.dp),
    ) { ms, cs ->
        val maxW = cs.maxWidth
        val indent = (size * 1.4f).sp.roundToPx()
        val gap = (size * .84f * .4f).sp.roundToPx()
        val wordH = (size * 1.45f).sp.roundToPx()
        val pl = ms.map { it.measure(Constraints(maxWidth = maxW)) }
        val xs = IntArray(pl.size)
        val rowOf = IntArray(pl.size)
        // разбиваем на группы (слова), группа не рвётся
        val groupIdx = ArrayList<IntRange>()
        var st = 0
        for (i in 1..kinds.size) if (i == kinds.size || kinds[i].first != kinds[st].first) { groupIdx.add(st until i); st = i }
        var row = 0
        var x = 0
        var chordEnd = Int.MIN_VALUE / 2
        var rowEmpty = true
        val tmp = IntArray(pl.size)
        // раскладывает группу с позиции; возвращает (конец слов, конец аккорда, правая граница)
        fun place(r: IntRange, sx: Int, ce: Int): Triple<Int, Int, Int> {
            var gx = sx; var e = ce
            for (i in r) {
                if (kinds[i].second) {
                    if (gx < e + gap) gx = e + gap
                    tmp[i] = gx; e = gx + pl[i].width
                } else { tmp[i] = gx; gx += pl[i].width }
            }
            return Triple(gx, e, maxOf(gx, e))
        }
        for (r in groupIdx) {
            var res = place(r, x, chordEnd)
            if (!rowEmpty && res.third > maxW) {
                row++; x = indent; chordEnd = Int.MIN_VALUE / 2
                res = place(r, x, chordEnd)
            }
            for (i in r) { xs[i] = tmp[i]; rowOf[i] = row }
            x = res.first; chordEnd = res.second; rowEmpty = false
        }
        val rows = row + 1
        val chordRowH = IntArray(rows)
        val rowH = IntArray(rows)
        pl.forEachIndexed { i, it ->
            if (kinds[i].second) chordRowH[rowOf[i]] = maxOf(chordRowH[rowOf[i]], it.height)
            else rowH[rowOf[i]] = maxOf(rowH[rowOf[i]], it.height, wordH)
        }
        val tops = IntArray(rows)
        var total = 0
        for (r in 0 until rows) { tops[r] = total; total += chordRowH[r] + rowH[r] }
        layout(maxW, total) {
            pl.forEachIndexed { i, it ->
                val r = rowOf[i]
                val y = if (kinds[i].second) tops[r] else tops[r] + chordRowH[r]
                it.placeRelative(xs[i], y)
            }
        }
    }
}

@Composable
private fun Tools(d: SongD, modifier: Modifier, onTr: (Int) -> Unit) {
    val p = LocalPal.current
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val bs = if (narrow) 40.dp else 44.dp
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.navigationBarsPadding().padding(bottom = 16.dp).pop(150), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(d.scrolling, enter = fadeIn() + slideInVertically(springy()) { it }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            Row(
                Modifier.padding(bottom = 8.dp).shadow(16.dp, shape).clip(shape).background(p.raised).border(1.dp, p.line, shape).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Ic.minus, "Медленнее", { if (d.speed > 0) d.speed-- }, size = 38.dp, plain = true, iconSize = 18.dp)
                Bump(d.speed) {
                    T("Скорость ${fmtSpeed(SPEEDS[it])}", body(13.sp, 800, 1.2f), modifier = Modifier.widthIn(min = 104.dp), align = TextAlign.Center)
                }
                IconBtn(Ic.plus, "Быстрее", { if (d.speed < SPEEDS.lastIndex) d.speed++ }, size = 38.dp, plain = true, iconSize = 18.dp)
            }
        }
        Row(
            Modifier.shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(p.raised).border(1.dp, p.line, shape).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconBtn(Ic.minus, "Тон ниже", { onTr(-1) }, size = bs, plain = true)
            Bump(d.tr) { tr ->
                Column(Modifier.widthIn(min = if (narrow) 30.dp else 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("ТОН", body(10.sp, 700, 1.1f), p.muted)
                    T((if (tr > 0) "+" else "") + tr, body(15.sp, 800, 1.1f))
                }
            }
            IconBtn(Ic.plus, "Тон выше", { onTr(1) }, size = bs, plain = true)
            Sep()
            TextIconBtn("A−", "Мельче", 14, { d.size = maxOf(14, d.size - 2) }, bs)
            TextIconBtn("A+", "Крупнее", 18, { d.size = minOf(34, d.size + 2) }, bs)
            Sep()
            PlayBtn(d.scrolling, bs) { d.scrolling = !d.scrolling }
            IconBtn(if (d.stage) Ic.shrink else Ic.expand, "Во весь экран", { d.stage = !d.stage }, size = bs, plain = !d.stage, on = d.stage)
        }
    }
}

private fun fmtSpeed(f: Float) = (if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString().replace('.', ',')) + "×"

@Composable
private fun Sep() = Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(28.dp).background(LocalPal.current.line))

@Composable
private fun PlayBtn(on: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val p = LocalPal.current
    val motion = LocalMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on && motion) while (true) { pulse.animateTo(1f, tween(800)); pulse.animateTo(0f, tween(800)) } else pulse.snapTo(0f)
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.drawWithContent {
            if (on) {
                val r = 6.dp.toPx() * pulse.value
                drawRoundRect(p.a2.copy(alpha = .35f * pulse.value), Offset(-r, -r), androidx.compose.ui.geometry.Size(this.size.width + 2 * r, this.size.height + 2 * r), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx() + r))
            }
            drawContent()
        }
    ) {
        AnimatedContent(on, transitionSpec = { (fadeIn(tween(180)) + androidx.compose.animation.scaleIn(springy(), .6f)) togetherWith fadeOut(tween(120)) }, label = "play") { o ->
            IconBtn(if (o) Ic.pause else Ic.play, "Автопрокрутка", onClick, size = size, plain = !o, on = o, iconSize = if (o) 22.dp else 20.dp)
        }
    }
}
