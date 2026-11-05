package ru.akkordnik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.akkordnik.app.data.Song
import ru.akkordnik.app.data.Store

private var keys = 0L

sealed class Dest(val depth: Int) { val key = ++keys }
class HomeD : Dest(0)
class SongD(val id: String) : Dest(1) {
    var tr by mutableIntStateOf(0)
    var size by mutableIntStateOf(-1)
    var stage by mutableStateOf(false)
    var scrolling by mutableStateOf(false)
    var speed by mutableIntStateOf(2) // индекс в SPEEDS
    val list = LazyListState()
}
class SettingsD : Dest(1)
class ImportD(val url: String? = null, val text: String? = null) : Dest(1)
class FormD(val id: String?, val f: FormState) : Dest(2)
class ChordD(val name: String, val form: FormState?) : Dest(3)

class AppState(val store: Store) {
    val stack = mutableStateListOf<Dest>(HomeD())
    val toast = ToastState()
    val dialogs = Dialogs()
    var query by mutableStateOf(TextFieldValue(""))
    val listState = LazyListState()
    var listShown = false

    val top get() = stack.last()
    fun push(d: Dest) { stack.add(d) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(d: Dest) { stack[stack.lastIndex] = d }
    fun resetTo(vararg d: Dest) { val home = stack.first(); stack.clear(); stack.add(home); d.forEach { stack.add(it) } }

    fun openSong(id: String) = push(SongD(id))
    fun newSong(draft: Song? = null) = push(FormD(null, FormState(null, draft ?: Song("", ""))))
    fun editSong(s: Song) = push(FormD(s.id, FormState(s.id, s)))

    /** Принять текст из «Поделиться». */
    fun takeShared(t0: String?) {
        val t = (t0 ?: "").trim()
        if (t.isEmpty()) return
        val m = Regex("https?://\\S+").find(t)
        val d = if (m != null && t.split("\n").size < 4) ImportD(url = m.value.trimEnd(')', ']', '.', ',', ';', '»', '"', '\'')) else ImportD(text = t)
        if (top is ImportD) replaceTop(d) else push(d)
    }
}

@Composable
fun App(app: AppState) {
    val db = app.store.db
    AkkTheme(db.settings.theme, db.settings.motion) {
        val p = LocalPal.current
        val scheme = if (p.light) lightColorScheme(primary = p.accent, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text)
        else darkColorScheme(primary = p.accent, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text)
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(p.accent, p.accent.copy(alpha = .32f))) {
                Box(Modifier.fillMaxSize().background(p.bg)) {
                    // Android 8 при открытии окна сам ставит фокус на первое поле ввода и показывает клавиатуру.
                    // Невидимая «ловушка» забирает этот стартовый фокус себе.
                    Box(Modifier.size(1.dp).focusable())
                    Glow()
                    Screens(app)
                    ToastHost(app.toast)
                    DialogHost(app.dialogs)
                }
            }
        }
    }
    BackHandler(enabled = app.stack.size > 1) { app.pop() }
}

@Composable
private fun Screens(app: AppState) {
    val motion = LocalMotion.current
    val shift = with(LocalDensity.current) { 36.dp.roundToPx() }
    AnimatedContent(
        targetState = app.top,
        contentKey = { it.key },
        transitionSpec = {
            if (!motion) EnterTransition.None togetherWith ExitTransition.None
            else {
                val from = initialState.depth
                val to = targetState.depth
                // «Общая ось» Material: старый экран быстро гаснет, новый проявляется следом — без наложения текста
                val dur = tween<androidx.compose.ui.unit.IntOffset>(340, easing = EaseApp)
                val inFade = fadeIn(tween(220, delayMillis = 80, easing = LinearOutSlowInEasing))
                val outFade = fadeOut(tween(80, easing = FastOutLinearInEasing))
                when {
                    to > from -> ContentTransform(
                        slideInHorizontally(dur) { shift } + inFade,
                        outFade + slideOutHorizontally(dur) { -shift / 2 },
                        targetContentZIndex = 1f,
                    )
                    to < from -> ContentTransform(
                        slideInHorizontally(dur) { -shift / 2 } + inFade,
                        outFade + slideOutHorizontally(dur) { shift },
                        targetContentZIndex = -1f,
                    )
                    else -> ContentTransform(
                        slideInVertically(tween(340, easing = EaseApp)) { shift / 4 } + inFade,
                        outFade,
                    )
                }
            }
        },
        label = "nav",
    ) { d ->
        val t0 = androidx.compose.runtime.remember { android.os.SystemClock.uptimeMillis() }
        CompositionLocalProvider(LocalScreenStart provides t0) {
            when (d) {
                is HomeD -> ListScreen(app)
                is SongD -> SongScreen(app, d)
                is SettingsD -> SettingsScreen(app)
                is ImportD -> ImportScreen(app, d)
                is FormD -> FormScreen(app, d)
                is ChordD -> ChordEditorScreen(app, d)
            }
        }
    }
}

fun plural(n: Int, a: String, b: String, c: String): String {
    val m = n % 10
    val h = n % 100
    return if (m == 1 && h != 11) a else if (m in 2..4 && (h < 10 || h >= 20)) b else c
}
