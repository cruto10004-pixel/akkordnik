package ru.akkordnik.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.akkordnik.app.data.ChordDef
import ru.akkordnik.app.data.Db
import ru.akkordnik.app.data.Settings
import ru.akkordnik.app.data.Song
import ru.akkordnik.app.data.Store
import ru.akkordnik.app.ui.App
import ru.akkordnik.app.ui.AppState
import ru.akkordnik.app.ui.ChordD
import ru.akkordnik.app.ui.FormD
import ru.akkordnik.app.ui.FormState
import ru.akkordnik.app.ui.ImportD
import ru.akkordnik.app.ui.SettingsD
import ru.akkordnik.app.ui.SongD
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class Shots {
    @get:Rule val rule = createComposeRule()

    private val out = File(System.getProperty("shots.dir") ?: "/data/native-tmp/shots").apply { mkdirs() }

    private val songs = listOf(
        Song("u1", "Династия (feat. Villian)", "madk1d", 2,
            "# Куплет 1\n[Am]Я не знаю, что такое [F]династия,\n[C]Но я знаю, как дол[G]го ждать,\n[Am]Мне малым-мало спало[F]ся, и так очень длинная строка чтобы проверить перенос\n\n# Припев\n[F]Ты моя [G]династия, [Am]я твой король\n[F]Мы с тобой [G]одной крови — [E7]это не боль"),
        Song("u2", "17 ножевых", "Свидание", 0, "[Проигрыш]\nСемнадцать ножевых,\nи все в моё сердце\n\n# Куплет\nЯ иду по улице одна", mode = "simple",
            sel = listOf("квинт 7 верх", "квинт 3 верх", "Am"), notes = "Вступление 2 раза, бой восьмёрка"),
        Song("u3", "Группа крови", "Кино", 0, "[Am]Тёплое место, но [C]улицы ждут", cover = testCover()),
    )
    private fun testCover(): String {
        val b = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(android.graphics.Color.rgb(30, 60, 120))
        c.drawCircle(60f, 60f, 40f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(250, 180, 40) })
        val o = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
        return "data:image/png;base64," + android.util.Base64.encodeToString(o.toByteArray(), android.util.Base64.NO_WRAP)
    }
    private val chords = mapOf(
        "квинт 7 верх" to ChordDef(listOf(-1, 7, 9, 9, -1, -1), false),
        "квинт 3 верх" to ChordDef(listOf(-1, 3, 5, 5, -1, -1), false),
    )

    private fun app(theme: String = "sunset"): AppState {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "akkordnik.json").delete()
        val st = Store(ctx)
        st.update { Db(songs, chords, Settings(theme = theme, motion = false, quick = Settings().quick + chords.keys)) }
        st.markReady()
        return AppState(st)
    }

    private fun shot(name: String, ms: Long = 2500, root: Int = 0) {
        rule.waitForIdle()
        val img = try {
            val nodes = rule.onAllNodes(isRoot())
            (if (root == 0) rule.onRoot() else nodes[root]).captureToImage().asAndroidBitmap()
        } catch (e: Throwable) {
            println("captureToImage failed: $e — drawing views manually")
            drawWindows()
        }
        File(out, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Рисуем все окна приложения (основное + диалоги/шторки) в один кадр. */
    private fun drawWindows(): Bitmap {
        val g = Class.forName("android.view.WindowManagerGlobal")
        val inst = g.getMethod("getInstance").invoke(null)
        val f = g.getDeclaredField("mRoots").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val roots = (f.get(inst) as List<Any>)
        val views = roots.map { it.javaClass.getMethod("getView").invoke(it) as android.view.View }
        val w = views.maxOf { it.width }; val h = views.maxOf { it.height }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        views.forEach { v -> val loc = IntArray(2); v.getLocationOnScreen(loc); c.save(); c.translate(loc[0].toFloat(), loc[1].toFloat()); v.draw(c); c.restore() }
        return bmp
    }

    private fun start(a: AppState) {
        rule.setContent { App(a) }
    }

    @Test fun list() { val a = app(); start(a); shot("01-list") }
    @Test fun listPaper() { val a = app("paper"); start(a); shot("01b-list-paper") }
    @Test fun empty() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "akkordnik.json").delete()
        val st = Store(ctx); st.update { it.copy(settings = it.settings.copy(motion = false)) }; st.markReady(); start(AppState(st)); shot("02-empty")
    }
    @Test fun song() { val a = app(); a.push(SongD("u1")); start(a); shot("03-song") }
    @Test fun songSimple() { val a = app("night"); a.push(SongD("u2")); start(a); shot("04-song-simple") }
    @Test fun sheet() {
        val a = app(); a.push(SongD("u1")); start(a); rule.waitForIdle()
        rule.onAllNodesWithText("Am").onFirst().performClick()
        shot("05-sheet", 3000, 1)
    }
    @Test fun formCover() { val a = app(); a.push(FormD("u3", FormState("u3", a.store.db.songs[2]))); start(a); shot("06b-form-cover") }
    @Test fun form() { val a = app(); a.push(FormD("u1", FormState("u1", a.store.db.songs[0]))); start(a); shot("06-form") }
    @Test fun formSimple() { val a = app("ocean"); a.push(FormD("u2", FormState("u2", a.store.db.songs[1]))); start(a); shot("07-form-simple") }
    @Test fun settings() { val a = app(); a.push(SettingsD()); start(a); shot("08-settings") }
    @Test fun import() { val a = app("neon"); a.push(ImportD()); start(a); shot("09-import") }
    @Test fun editor() { val a = app(); a.push(ChordD("Am", null)); start(a); shot("10-editor") }

    /** Покадрово снимает переход: проверяем, что анимации идут плавно и без «вспышек». */
    private fun film(name: String, steps: List<Long>, action: () -> Unit) {
        rule.mainClock.autoAdvance = false
        action()
        val frames = ArrayList<Bitmap>()
        var prev = 0L
        for (t in steps) {
            rule.mainClock.advanceTimeBy(t - prev); prev = t
            frames.add(try { rule.onRoot().captureToImage().asAndroidBitmap() } catch (e: Throwable) { drawWindows() })
        }
        rule.mainClock.autoAdvance = true
        val w = 300; val h = (frames[0].height * w / frames[0].width)
        val strip = Bitmap.createBitmap(w * frames.size + 6 * (frames.size - 1), h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(strip); c.drawColor(android.graphics.Color.WHITE)
        frames.forEachIndexed { i, f -> c.drawBitmap(Bitmap.createScaledBitmap(f, w, h, true), (i * (w + 6)).toFloat(), 0f, null) }
        File(out, "$name.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun anim() {
        val a = app(); a.store.update { it.copy(settings = it.settings.copy(motion = true)) }
        start(a); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        val steps = listOf(0L, 48, 96, 160, 240, 340, 460, 700)
        film("anim-1-push", steps) { a.openSong("u1") }
        rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        film("anim-2-pop", steps) { a.pop() }
        rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        film("anim-3-theme", listOf(0L, 100, 200, 350, 600)) { a.store.update { it.copy(settings = it.settings.copy(theme = "ocean")) } }
    }
}
