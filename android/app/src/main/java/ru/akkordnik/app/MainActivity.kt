package ru.akkordnik.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import ru.akkordnik.app.data.Store
import ru.akkordnik.app.ui.App
import ru.akkordnik.app.ui.BtnKind
import ru.akkordnik.app.ui.DBtn
import ru.akkordnik.app.ui.Ic
import ru.akkordnik.app.ui.AppState
import ru.akkordnik.app.ui.THEMES

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState
    private var lightBars: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val store = Store(applicationContext)
        app = AppState(store)
        super.onCreate(savedInstanceState)
        bars(THEMES[store.db.settings.theme]?.light == true)

        val prefs = getSharedPreferences("akk", MODE_PRIVATE)
        // Переносим данные, если ещё не переносили или если файла с песнями нет
        // (например, прошлая версия упала сразу после переноса и не успела его записать).
        if (!prefs.getBoolean("migrated", false) || !store.exists) {
            Migration.run(this) { raw, ok ->
                if (ok) {
                    val n = store.migrate(raw)
                    prefs.edit().putBoolean("migrated", true).apply()
                    if (n > 0) app.toast.show("Песни перенесены из прошлой версии: $n")
                } else store.markReady()
            }
        } else store.markReady()

        if (savedInstanceState == null) { handleShare(intent); offerCrashReport() }
        setContent {
            val light = THEMES[store.db.settings.theme]?.light == true
            LaunchedEffect(light) { bars(light) }
            App(app)
        }
    }

    private fun offerCrashReport() {
        val f = AkkApp.crashFile(this)
        val report = try { if (f.exists()) f.readText().also { f.delete() } else null } catch (_: Throwable) { null } ?: return
        app.dialogs.ask(
            "Прошлый запуск завершился ошибкой",
            "Скопируйте отчёт и пришлите его — так ошибку получится быстро найти и исправить.",
            listOf(DBtn("Скопировать отчёт", "copy", icon = Ic.paste), DBtn("Закрыть", null, BtnKind.Ghost))
        ) { v ->
            if (v == "copy") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Отчёт Аккордника", report))
                app.toast.show("Отчёт скопирован")
            }
        }
    }

    private fun bars(light: Boolean) {
        if (lightBars == light) return
        lightBars = light
        // На Android 8.0 иконки навигации всегда светлые — в светлой теме подкладываем полупрозрачную подложку
        val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1b, 0x1b, 0x1b)) else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(style, style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val t = listOfNotNull(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT))
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            app.takeShared(if (Regex("https?://").containsMatchIn(text)) text else t.joinToString("\n"))
        }
    }
}
