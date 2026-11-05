package ru.akkordnik.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.akkordnik.app.data.Db
import ru.akkordnik.app.data.Settings
import ru.akkordnik.app.data.Song
import ru.akkordnik.app.data.toJson
import java.io.File
import java.util.concurrent.TimeUnit

/** Запуск настоящей MainActivity: тема, перенос данных из старой версии, первый кадр. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test fun launches() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // бесконечный фон-анимацию Robolectric не «прокручивает», поэтому выключаем анимации
        val db = Db(listOf(Song("u1", "Группа крови", "Кино", 0, "[Am]Тёплое место, но [C]улицы ждут")), emptyMap(), Settings(motion = false))
        File(ctx.filesDir, "akkordnik.json").writeText(db.toJson().toString())
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            ShadowLooper.idleMainLooper(7, TimeUnit.SECONDS)
            sc.onActivity { a -> assertFalse(a.isFinishing) }
        }
        assertFalse(AkkApp.crashFile(ctx).exists())
    }
}
