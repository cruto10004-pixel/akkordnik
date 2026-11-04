package ru.akkordnik.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.akkordnik.app.BuildConfig
import ru.akkordnik.app.data.ChordDef
import ru.akkordnik.app.data.dbFromJson
import ru.akkordnik.app.data.toJson
import ru.akkordnik.app.music.Voicing
import ru.akkordnik.app.music.barreInfo
import ru.akkordnik.app.music.fmt

@Composable
fun SettingsScreen(app: AppState) {
    val p = LocalPal.current
    val db = app.store.db
    val S = db.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun set(f: (ru.akkordnik.app.data.Settings) -> ru.akkordnik.app.data.Settings) = app.store.update { it.copy(settings = f(it.settings)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(app.store.db.toJson(export = true).toString(1).toByteArray()) } }.isSuccess
            }
            app.toast.show(if (ok) "Резервная копия сохранена" else "Не удалось сохранить копию")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val o = JSONObject(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
                    if (o.optJSONArray("songs") == null) null else dbFromJson(o)
                }.getOrElse { Unit }
            }
            when (parsed) {
                is ru.akkordnik.app.data.Db -> {
                    val n = parsed.songs.size
                    app.dialogs.ask(
                        "Загрузить данные?", "В файле $n ${plural(n, "песня", "песни", "песен")} и ${parsed.chords.size} ${plural(parsed.chords.size, "аккорд", "аккорда", "аккордов")}. Как загрузить?",
                        listOf(DBtn("Добавить к моим", "merge"), DBtn("Заменить всё", "replace", BtnKind.Danger), DBtn("Отмена", null, BtnKind.Ghost))
                    ) { m -> if (m != null) { app.store.importData(parsed, m == "replace"); app.toast.show("Данные загружены") } }
                }
                null -> app.toast.show("В файле нет песен")
                else -> app.toast.show("Это не файл Аккордника")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Настройки", onBack = { app.pop() })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 16.dp, bottom = 40.dp)) {
            Section("Тема", 0) {
                val rows = THEME_NAMES.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            r.forEach { (k, l) -> ThemeCard(k, l, S.theme == k, Modifier.weight(1f)) { set { s -> s.copy(theme = k) } } }
                            if (r.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Section("Отображение", 1) {
                Card {
                    SettingRow("Размер текста", "Для экрана песни", first = true) {
                        Stepper("${S.size}", { set { it.copy(size = maxOf(14, it.size - 2)) } }, { set { it.copy(size = minOf(34, it.size + 2)) } })
                    }
                    SettingRow("Обозначение си", "B — международное, H — как в школе") {
                        Segmented(listOf("B", "H"), if (S.notation == "H") 1 else 0, { i -> set { it.copy(notation = if (i == 1) "H" else "B") } },
                            Modifier.width(104.dp), height = 36.dp, radius = 9.dp)
                    }
                    SettingRow("Анимации", "Переходы и эффекты") { Toggle(S.motion) { v -> set { it.copy(motion = v) } } }
                }
            }
            Section("Мои аккорды", 2, pad = false) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp)) {
                    item(key = "+") {
                        val shape = RoundedCornerShape(14.dp)
                        Column(
                            Modifier.tap { app.push(ChordD("", null)) }.width(96.dp).heightIn(min = 126.dp).clip(shape).dashed(p.line.copy(alpha = .3f), 14.dp, 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                        ) {
                            Icon(Ic.plus, null, Modifier.size(26.dp), tint = p.muted)
                            T("Новый", body(13.sp, 700, 1.2f), p.muted)
                        }
                    }
                    items(db.chords.entries.toList(), key = { it.key }) { (k, c) -> MyChord(k, c, Modifier.animateItem()) { app.push(ChordD(k, null)) } }
                }
                if (db.chords.isEmpty()) Hint("Создайте свою аппликатуру или добавьте фото к любому аккорду из песни.", Modifier.padding(horizontal = 20.dp))
            }
            Section("Данные", 3) {
                val nCh = db.chords.size
                val nPh = db.chords.values.count { it.photo != null }
                Card {
                    SettingRow(
                        "${db.songs.size} ${plural(db.songs.size, "песня", "песни", "песен")} · $nCh ${plural(nCh, "аккорд", "аккорда", "аккордов")} · $nPh фото",
                        "Хранится на этом устройстве", first = true
                    )
                    SettingRow("Выгрузить всё", "Файл .json с песнями, аккордами, фото и настройками") {
                        Btn(null, { exportLauncher.launch("akkordnik-" + java.time.LocalDate.now() + ".json") }, icon = Ic.download, height = 44.dp, padH = 14.dp)
                    }
                    SettingRow("Загрузить из файла", "Восстановить или перенести с другого устройства") {
                        Btn(null, { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, icon = Ic.upload, kind = BtnKind.Ghost, height = 44.dp, padH = 14.dp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp).rise(4), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    buildAnnotatedString {
                        append("Аккордник • owner ")
                        withStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold)) { append("@prelestnly") }
                    },
                    Modifier.clip(RoundedCornerShape(8.dp)).tap(scale = .96f) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/prelestnly")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = body(13.sp, 500, 1.4f), color = p.muted, textAlign = TextAlign.Center
                )
                T("Версия ${BuildConfig.VERSION_NAME}", body(12.sp, 500, 1.4f), p.muted.copy(alpha = .45f))
            }
        }
    }
}

@Composable
private fun Section(title: String, i: Int, pad: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 8.dp, bottom = 22.dp).rise(i)) {
        Caps(title, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp))
        Box(if (pad) Modifier.padding(horizontal = 20.dp) else Modifier) { content() }
    }
}

@Composable
private fun ThemeCard(key: String, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val t = THEMES.getValue(key)
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(if (on) p.accent else p.line, tween(250), label = "tb")
    Column(modifier.tap(scale = .96f, haptic = true, onClick = onClick).clip(shape).background(p.surface).border(2.dp, border, shape).padding(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))) {
            drawRect(t.bg)
            val l = 10.dp.toPx(); val h = 8.dp.toPx(); val cr = CornerRadius(4.dp.toPx())
            drawRoundRect(Brush.horizontalGradient(listOf(t.a1, t.a2)), Offset(l, 12.dp.toPx()), Size(size.width - l - 30.dp.toPx(), h), cr)
            drawRoundRect(t.accent, Offset(l, 28.dp.toPx()), Size(size.width - l - 60.dp.toPx(), h), cr)
            drawRoundRect(t.text.copy(alpha = .5f), Offset(l, 44.dp.toPx()), Size(size.width - l - 46.dp.toPx(), h), cr)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, body(14.sp, 800, 1.3f), modifier = Modifier.weight(1f))
            AnimatedVisibility(on, enter = scaleIn(springy()), exit = fadeOut(tween(120))) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(p.grad), contentAlignment = Alignment.Center) {
                    Icon(Ic.check, null, Modifier.size(14.dp), tint = p.onGrad)
                }
            }
        }
    }
}

@Composable
private fun MyChord(k: String, c: ChordDef, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(14.dp)
    val tm = rememberTextMeasurer()
    Column(modifier.tap(onClick = onClick).width(96.dp).clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (c.photo != null) {
                val bmp by rememberPhoto(c.photo)
                bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            } else if (c.frets != null) {
                val (b, bf) = barreInfo(c.frets, c.barre)
                val v = Voicing(c.frets, k, b, bf, custom = true)
                Canvas(Modifier.fillMaxSize()) {
                    // как object-fit: cover для схемы 230×280
                    val sc = maxOf(size.width / 230f, size.height / 280f)
                    val dx = (size.width - 230f * sc) / 2; val dy = (size.height - 280f * sc) / 2
                    translate(dx, dy) { scale(sc, sc, pivot = Offset.Zero) { drawDiagram(v, p, tm, 5000f) } }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        T(fmt(k), mono(14.sp, 800, 1.2f), p.accent, maxLines = 1)
    }
}
