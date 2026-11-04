package ru.akkordnik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.akkordnik.app.data.Song
import ru.akkordnik.app.music.chordsOf
import ru.akkordnik.app.music.fmt
import ru.akkordnik.app.music.hostOf
import ru.akkordnik.app.music.normText
import ru.akkordnik.app.music.simpleNotes
import ru.akkordnik.app.music.simpleSel

private fun tfv(s: String) = TextFieldValue(s, TextRange(s.length))
private val ANY_BR = Regex("\\[[^\\]]*\\]")
private val SP2 = Regex("\\s{2,}")
private val HAS_CH = Regex("\\[[A-H]")

class FormState(val id: String?, s: Song) {
    var title by mutableStateOf(tfv(s.title))
    var artist by mutableStateOf(tfv(s.artist))
    var capo by mutableIntStateOf(s.capo)
    var mode by mutableStateOf(s.mode)
    var text by mutableStateOf(tfv(s.text))
    var notes by mutableStateOf(tfv(simpleNotes(s)))
    val sel = mutableStateListOf<String>().apply { addAll(simpleSel(s)) }
    val source = s.source
    private var stash: Pair<String, String>? = null
    private fun snap() = listOf(title.text, artist.text, capo, mode, text.text, notes.text, sel.toList())
    private val initial: List<Any> = if (id == null && s.text.isNotEmpty()) emptyList() else snap()
    val dirty get() = snap() != initial

    init { if (mode == "simple" && sel.isEmpty()) sel.addAll(chordsOf(text.text)) }

    private fun applyMode(m: String) {
        mode = m
        if (m == "simple" && sel.isEmpty()) sel.addAll(chordsOf(text.text))
    }

    /** true — если аккорды перенесены наверх (для тоста) */
    fun switchMode(m: String): Boolean {
        if (m == mode) return false
        val st = stash
        if (m == "simple" && HAS_CH.containsMatchIn(text.text)) {
            applyMode(m)
            val plain = text.text.split("\n").joinToString("\n") { it.replace(ANY_BR, "").replace(SP2, " ").trimEnd() }
            stash = text.text to plain
            text = tfv(plain)
            return true
        } else if (m == "inline" && st != null && text.text == st.second) {
            text = tfv(st.first); stash = null; applyMode(m)
        } else applyMode(m)
        return false
    }

    fun insert(c: String) {
        if (mode == "simple") { if (c in sel) sel.remove(c) else sel.add(c); return }
        val v = text
        val a = minOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
        val b = maxOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
        val t = "[$c]"
        text = TextFieldValue(v.text.substring(0, a) + t + v.text.substring(b), TextRange(a + t.length))
    }

    fun addPending(c: String) { if (!(mode == "simple" && c in sel)) insert(c) }

    fun toSong(newId: String) = Song(
        id = newId, title = title.text.trim(), artist = artist.text.trim(), capo = capo, text = normText(text.text), mode = mode,
        sel = if (mode == "simple") sel.toList() else null, notes = if (mode == "simple") notes.text.trim() else null, source = source,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormScreen(app: AppState, d: FormD) {
    val p = LocalPal.current
    val f = d.f
    val db = app.store.db
    val titleFocus = remember { FocusRequester() }
    val uri = LocalUriHandler.current

    fun leave() {
        if (f.dirty) app.dialogs.ask(
            "Выйти без сохранения?", "Изменения в песне не сохранятся.",
            listOf(DBtn("Выйти без сохранения", "y", BtnKind.Danger), DBtn("Остаться", null, BtnKind.Ghost))
        ) { if (it == "y") app.pop() }
        else app.pop()
    }
    BackHandler { leave() }

    fun save() {
        if (f.title.text.isBlank()) { titleFocus.requestFocus(); app.toast.show("Введите название"); return }
        val id = f.id ?: ("u" + System.currentTimeMillis())
        val song = f.toSong(id)
        app.store.update { cur ->
            if (f.id != null && cur.songs.any { it.id == f.id }) cur.copy(songs = cur.songs.map { if (it.id == f.id) song else it })
            else cur.copy(songs = cur.songs + song)
        }
        app.toast.show("Песня сохранена")
        val prev = app.stack.getOrNull(app.stack.size - 2)
        if (f.id != null && prev is SongD && prev.id == f.id) app.pop() else app.resetTo(SongD(id))
    }

    fun delete() {
        val id = f.id ?: return
        app.dialogs.ask(
            "Удалить песню?", "«${f.title.text}» будет удалена без возможности восстановления.",
            listOf(DBtn("Удалить", "y", BtnKind.Danger, Ic.trash), DBtn("Отмена", null, BtnKind.Ghost))
        ) {
            if (it == "y") {
                app.store.update { cur -> cur.copy(songs = cur.songs.filter { s -> s.id != id }) }
                app.toast.show("Песня удалена")
                app.resetTo()
            }
        }
    }

    val simple = f.mode == "simple"
    val palette = remember(db.settings.quick, db.chords) { (db.settings.quick + db.chords.keys).distinct() }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (f.id != null) "Редактировать" else "Новая песня", onBack = { leave() })
        Column(
            Modifier.fillMaxWidth().weight(1f).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(Modifier.rise(0)) {
                FieldLabel("Название")
                Field(f.title, { f.title = it }, Modifier.focusRequester(titleFocus), placeholder = "Например, Чёрный ворон",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            Column(Modifier.rise(1)) {
                FieldLabel("Исполнитель")
                Field(f.artist, { f.artist = it }, placeholder = "Необязательно", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }
            Column(Modifier.rise(2)) {
                FieldLabel("Каподастр")
                CapoPicker(f.capo) { f.capo = it }
            }
            Column(Modifier.rise(3)) {
                FieldLabel("Как записать аккорды")
                Segmented(listOf("Над словами", "Сверху отдельно"), if (simple) 1 else 0, {
                    if (f.switchMode(if (it == 1) "simple" else "inline")) app.toast.show("Аккорды из текста перенесены наверх")
                }, Modifier.fillMaxWidth(), height = 44.dp)
                AnimatedContent(simple, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "mh") { s ->
                    Hint(if (s) "Аккорды показываются сверху, текст — без аккордов. Быстро и просто." else "Аккорды стоят над нужными слогами — точно, как в песеннике.")
                }
            }
            Column(Modifier.rise(4).animateContentSize(tween(300, easing = EaseApp))) {
                FieldLabel(if (simple) "Аккорды песни" else "Текст с аккордами")
                AnimatedVisibility(simple, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    SelBox(f)
                }
                FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { c ->
                        ChordChip(fmt(c), { f.insert(c) }, height = 42.dp, fontSize = 14, picked = simple && c in f.sel)
                    }
                    AddChip("Аккорд", { app.push(ChordD("", f)) })
                }
                if (simple) {
                    Hint("Нажимайте на аккорды, чтобы добавить их в песню. Нажмите на выбранный сверху — он уберётся.", Modifier.offset(y = (-10).dp))
                    FieldLabel("Пометки", Modifier.padding(top = 10.dp))
                    Field(f.notes, { f.notes = it }, placeholder = "Например: всю песню по кругу, бой шестёрка, вступление 2 раза",
                        singleLine = false, minHeight = 90.dp, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    FieldLabel("Текст песни", Modifier.padding(top = 18.dp))
                }
                Field(
                    f.text, { f.text = it },
                    placeholder = if (simple) "Ой, то не вечер, то не вечер,\nМне малым-мало спалося…" else "[Am]Ой, то не вечер, то не [Dm]вечер",
                    singleLine = false, minHeight = 300.dp, style = mono(15.sp, 500, 1.65f),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                )
                val code = SpanStyle(color = p.accent, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text(
                    buildAnnotatedString {
                        if (simple) { append("Просто текст. Заголовок раздела: "); withStyle(code) { append("# Припев") }; append(".") }
                        else {
                            append("Нажмите на аккорд — он вставится туда, где курсор. Или пишите вручную: ")
                            withStyle(code) { append("[Am]слово") }; append(". Заголовок раздела: ")
                            withStyle(code) { append("# Припев") }; append(".")
                        }
                    },
                    Modifier.padding(top = 8.dp), style = body(14.sp, 500, 1.45f), color = p.muted
                )
            }
            if (f.source.isNotEmpty()) {
                Row(Modifier.offset(y = (-6).dp)) {
                    T("Импортировано: ", body(13.sp, 500, 1.4f), p.muted)
                    T(hostOf(f.source), body(13.sp, 700, 1.4f), p.accent, Modifier.tap { runCatching { uri.openUri(f.source) } })
                }
            }
            Row(Modifier.rise(5), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Сохранить", { save() }, Modifier.weight(1f), Ic.check)
                if (f.id != null) Btn("Удалить", { delete() }, Modifier.weight(1f), Ic.trash, BtnKind.Danger)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelBox(f: FormState) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    FlowRow(
        Modifier.padding(bottom = 12.dp).fillMaxWidth().heightIn(min = 62.dp).clip(shape)
            .background(p.surface.copy(alpha = .7f)).dashed(p.accent.copy(alpha = .45f), 16.dp).padding(10.dp)
            .animateContentSize(tween(260, easing = EaseApp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (f.sel.isEmpty()) Box(Modifier.height(42.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
            T("Аккорды пока не выбраны", body(14.sp, 500, 1.3f), p.muted)
        }
        f.sel.toList().forEach { c ->
            key(c) {
                Box(Modifier.pop(always = true)) { ChordChip(fmt(c), { f.sel.remove(c) }, height = 42.dp, fontSize = 14, selected = true, trailing = "×") }
            }
        }
    }
}

@Composable
private fun CapoPicker(value: Int, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    val st = rememberLazyListState()
    val density = LocalDensity.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        // ждём первую раскладку, затем ставим выбранный лад по центру
        val vw = snapshotFlow { st.layoutInfo.viewportSize.width }.first { it > 0 }
        val w = with(density) { (if (value == 0) 86.dp else 58.dp).roundToPx() }
        val pad = with(density) { 20.dp.roundToPx() }
        val off = -(vw / 2 - w / 2 - pad)
        if (first) st.scrollToItem(value, off) else st.animateScrollToItem(value, off)
        first = false
    }
    LazyRow(
        state = st, modifier = Modifier.bleed(20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp, start = 20.dp, end = 20.dp)
    ) {
        items(13) { n ->
            val on = n == value
            val lift by animateDpAsState(if (on) (-2).dp else 0.dp, springy(), label = "lift")
            val shape = RoundedCornerShape(14.dp)
            var m = Modifier.offset(y = lift).tap(haptic = true) { onChange(n) }.width(if (n == 0) 86.dp else 58.dp).height(66.dp)
            m = if (on) m.shadow(12.dp, shape, ambientColor = p.a2, spotColor = p.a2).clip(shape).background(p.grad)
            else m.clip(shape).background(p.surface).border(1.dp, p.line, shape)
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                T(if (n == 0) "—" else "$n", display(20.sp, 800, 1f), if (on) p.onGrad else p.text)
                Spacer(Modifier.height(4.dp))
                T(if (n == 0) "без капо" else "лад", body(11.sp, 700, 1f), if (on) p.onGrad.copy(alpha = .8f) else p.muted)
            }
        }
    }
}
