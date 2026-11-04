package ru.akkordnik.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.akkordnik.app.music.NoChordsException
import ru.akkordnik.app.music.fetchSong
import ru.akkordnik.app.music.textToSong

private class Status(val msg: String, val kind: Int) // 0 загрузка, 1 ошибка

private val URL_ONLY = Regex("^\\s*https?://\\S+\\s*$")
private val URL_IN = Regex("https?://\\S+")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(app: AppState, d: ImportD) {
    val p = LocalPal.current
    var url by remember { mutableStateOf(TextFieldValue(d.url ?: "")) }
    var raw by remember { mutableStateOf(TextFieldValue(d.text ?: "")) }
    var st1 by remember { mutableStateOf<Status?>(null) }
    var st2 by remember { mutableStateOf<Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val urlFocus = remember { FocusRequester() }
    val rawFocus = remember { FocusRequester() }

    fun done(song: ru.akkordnik.app.data.Song) {
        app.toast.show("Песня распознана — проверьте и сохраните")
        app.newSong(song)
    }

    fun run() {
        if (busy) return
        var u = url.text.trim()
        URL_IN.find(u)?.let { u = it.value }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            st1 = Status("Вставьте ссылку, которая начинается с https://", 1); urlFocus.requestFocus(); return
        }
        focus.clearFocus()
        busy = true
        st1 = Status("Загружаю страницу…", 0)
        scope.launch {
            try {
                val song = withContext(Dispatchers.IO) { fetchSong(u) }
                st1 = null
                done(song)
            } catch (e: NoChordsException) {
                st1 = Status("На странице не нашлось аккордов. Попробуйте вставить текст ниже.", 1)
            } catch (e: Exception) {
                val why = when (e) {
                    is java.net.UnknownHostException -> "нет интернета или сайт недоступен"
                    is java.net.SocketTimeoutException -> "сайт долго не отвечает"
                    is org.jsoup.HttpStatusException -> "ошибка ${e.statusCode}"
                    else -> e.message ?: e.javaClass.simpleName
                }
                st1 = Status("Не удалось загрузить страницу ($why). Проверьте ссылку или вставьте текст ниже.", 1)
            } finally { busy = false }
        }
    }

    fun parse() {
        val t = raw.text
        if (URL_ONLY.matches(t)) { url = TextFieldValue(t.trim()); raw = TextFieldValue(""); run(); return }
        try { st2 = null; done(textToSong(t)) } catch (_: Exception) {
            st2 = Status("Не нашёл аккордов. Аккорды должны стоять отдельной строкой над словами или в [скобках].", 1)
        }
    }

    LaunchedEffect(Unit) {
        if (!d.url.isNullOrEmpty()) run()
        else if (!d.text.isNullOrEmpty()) parse()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Импорт песни", "Перенесём с сайта в удобный вид", onBack = { app.pop() })
        Column(
            Modifier.fillMaxWidth().weight(1f).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.rise(0)) {
                Column(Modifier.padding(16.dp).animateContentSize(tween(260, easing = EaseApp))) {
                    T("По ссылке", display(17.sp, 700, 1.3f))
                    Spacer(Modifier.height(4.dp))
                    T("Откройте песню на сайте с аккордами, скопируйте адрес и вставьте сюда. Или нажмите «Поделиться → Аккордник» прямо в браузере.", body(14.sp, 500, 1.45f), p.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(
                            url, { url = it }, Modifier.weight(1f).focusRequester(urlFocus), placeholder = "https://amdm.ru/akkordi/…",
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            actions = KeyboardActions(onGo = { run() })
                        )
                        Box(Modifier.size(52.dp)) {
                            Btn(null, { run() }, Modifier.size(52.dp), icon = if (busy) null else Ic.download, height = 52.dp, padH = 0.dp, enabled = !busy)
                            if (busy) Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Spinner(20.dp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AmDm.ru", "akkords.pro", "и другие сайты").forEach {
                            Box(Modifier.clip(CircleShape).border(1.dp, p.line, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)) { T(it, body(13.sp, 700, 1.2f), p.muted) }
                        }
                    }
                    StatusBox(st1)
                }
            }
            Row(Modifier.rise(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(p.line))
                Caps("или")
                Box(Modifier.weight(1f).height(1.dp).background(p.line))
            }
            Card(Modifier.rise(2)) {
                Column(Modifier.padding(16.dp).animateContentSize(tween(260, easing = EaseApp))) {
                    T("Вставить текст", display(17.sp, 700, 1.3f))
                    Spacer(Modifier.height(4.dp))
                    T("Выделите на сайте текст песни вместе с аккордами, скопируйте и вставьте. Аккорды над строками распознаются сами.", body(14.sp, 500, 1.45f), p.muted)
                    Spacer(Modifier.height(12.dp))
                    Field(raw, { raw = it }, Modifier.focusRequester(rawFocus), placeholder = "Am        Dm\nОй, то не вечер, то не вечер…", singleLine = false, minHeight = 180.dp,
                        style = mono(14.sp, 500, 1.55f), keyboard = KeyboardOptions(autoCorrectEnabled = false))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("Вставить", {
                            val t = clip.getText()?.text ?: ""
                            if (t.isBlank()) st2 = Status("Буфер обмена пуст — сначала скопируйте текст песни.", 1)
                            else if (URL_ONLY.matches(t)) { url = TextFieldValue(t.trim(), TextRange(t.trim().length)); run() }
                            else { raw = TextFieldValue(t, TextRange(t.length)); parse() }
                        }, icon = Ic.paste, kind = BtnKind.Ghost, padH = 16.dp)
                        Btn("Распознать", { parse() }, Modifier.weight(1f), Ic.check)
                    }
                    StatusBox(st2)
                }
            }
        }
    }
}

@Composable
private fun StatusBox(s: Status?) {
    val p = LocalPal.current
    var last by remember { mutableStateOf(s) }
    if (s != null) last = s
    AnimatedVisibility(s != null, enter = fadeIn(tween(250)) + expandVertically(tween(300, easing = EaseApp)), exit = fadeOut(tween(150)) + shrinkVertically(tween(250))) {
        val x = last ?: return@AnimatedVisibility
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (x.kind == 1) p.danger.copy(alpha = .14f) else p.raised).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (x.kind == 0) { Spinner(); Spacer(Modifier.width(10.dp)) }
            T(x.msg, body(14.sp, 600, 1.45f))
        }
    }
}
