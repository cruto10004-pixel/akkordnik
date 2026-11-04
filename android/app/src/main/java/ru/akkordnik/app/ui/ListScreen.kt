package ru.akkordnik.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.geometry.Offset
import ru.akkordnik.app.data.Song
import ru.akkordnik.app.music.fmt
import ru.akkordnik.app.music.songChords

@Composable
fun ListScreen(app: AppState) {
    val p = LocalPal.current
    val db = app.store.db
    val entered = remember { SystemClock.uptimeMillis() }
    val first = remember { val f = !app.listShown; app.listShown = true; f }
    val fresh = first && SystemClock.uptimeMillis() - entered < 700
    val q = app.query.text.trim().lowercase()
    val list = remember(db.songs, q) {
        val coll = java.text.Collator.getInstance(java.util.Locale("ru"))
        db.songs.filter { (it.title + " " + it.artist).lowercase().contains(q) }.sortedWith { a, b -> coll.compare(a.title, b.title) }
    }
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = app.listState,
            contentPadding = PaddingValues(bottom = 130.dp),
        ) {
            item(key = "head") {
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f).rise(0, fresh)) {
                        T("Мой песенник", body(14.sp, 700, 1.3f), p.muted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildAnnotatedString {
                                append("Аккорд")
                                withStyle(SpanStyle(brush = Brush.linearGradient(listOf(p.a1, p.a2)))) { append("ник") }
                            },
                            style = display(32.sp, 800, 1.1f), color = p.text,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.rise(1, fresh)) {
                        IconBtn(Ic.link, "Импорт по ссылке", { app.push(ImportD()) })
                        IconBtn(Ic.gear, "Настройки", { app.push(SettingsD()) })
                    }
                }
            }
            item(key = "search") {
                Field(
                    app.query, { app.query = it }, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp).rise(2, fresh),
                    placeholder = "Поиск песни или исполнителя",
                    padding = PaddingValues(start = 0.dp, end = 12.dp, top = 15.dp, bottom = 15.dp),
                    keyboard = KeyboardOptions(imeAction = ImeAction.Search),
                    actions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    leading = { Icon(Ic.search, null, Modifier.padding(start = 15.dp, end = 11.dp).size(22.dp), tint = p.muted) },
                    trailing = {
                        AnimatedVisibility(app.query.text.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            IconBtn(Ic.close, "Очистить", { app.query = TextFieldValue("") }, Modifier.padding(end = 4.dp), size = 40.dp, plain = true, iconSize = 18.dp, tint = p.muted)
                        }
                    }
                )
            }
            if (db.songs.isNotEmpty()) item(key = "count") {
                T("${list.size} ${plural(list.size, "песня", "песни", "песен")}", body(14.sp, 600, 1.3f), p.muted, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            }
            if (!app.store.ready) item(key = "load") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Spinner(28.dp) }
            } else if (list.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp).rise(3, fresh), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (db.songs.isEmpty()) {
                        T("🎸", body(44.sp, 500, 1.2f), modifier = Modifier.pop(200))
                        Spacer(Modifier.height(8.dp))
                        T("Песенник пока пуст", body(18.sp, 800, 1.3f), align = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        T("Нажмите «Добавить песню» или импортируйте песню по ссылке с AmDm.ru, akkords.pro и других сайтов.", body(16.sp, 500, 1.5f), p.muted, align = TextAlign.Center)
                    } else T("Ничего не найдено.", body(16.sp, 500, 1.5f), p.muted, align = TextAlign.Center)
                }
            }
            itemsIndexedSongs(list, fresh) { app.openSong(it.id) }
        }
        Btn(
            "Добавить песню", { app.newSong() },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 22.dp).pop(250, fresh).height(58.dp),
            icon = Ic.plus, height = 58.dp, padH = 24.dp,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedSongs(list: List<Song>, fresh: Boolean, open: (Song) -> Unit) {
    items(list.size, key = { list[it].id }) { i ->
        SongRow(list[i], Modifier.animateItem().rise(i + 3, fresh && i < 14)) { open(list[i]) }
    }
}

@Composable
private fun SongRow(s: Song, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val chords = remember(s) { songChords(s).take(4).joinToString(" ") { fmt(it) } }
    Row(
        modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).tap(scale = .97f, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(54.dp).shadow(10.dp, RoundedCornerShape(15.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(15.dp)).background(coverGrad(s.id)),
            contentAlignment = Alignment.Center
        ) {
            T(
                (s.title.trim().firstOrNull()?.uppercase() ?: "♪"),
                display(20.sp, 800, 1f).copy(shadow = Shadow(Color.Black.copy(alpha = .25f), Offset(0f, 2f), 12f)), Color.White
            )
        }
        Column(Modifier.weight(1f)) {
            T(s.title, body(17.sp, 800, 1.35f), maxLines = 1)
            T(
                (s.artist.ifEmpty { "Без исполнителя" }) + if (chords.isNotEmpty()) " · $chords" else "",
                body(14.sp, 500, 1.4f), p.muted, maxLines = 1
            )
        }
        if (s.capo > 0) Box(Modifier.clip(CircleShape).background(p.grad).padding(horizontal = 11.dp, vertical = 5.dp)) {
            T("Капо ${s.capo}", body(13.sp, 800, 1.2f), p.onGrad, maxLines = 1)
        } else Box(Modifier.clip(CircleShape).border(1.dp, p.line, CircleShape).padding(horizontal = 11.dp, vertical = 5.dp)) {
            T("Без капо", body(13.sp, 700, 1.2f), p.muted, maxLines = 1)
        }
    }
}
