package ru.akkordnik.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.akkordnik.app.data.Store
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = File(ctx.filesDir, "akkordnik.json")
    private fun waitFile() { repeat(50) { if (file.exists()) return; Thread.sleep(50) } }

    @Test fun emptyMigrationCreatesFile() {
        file.delete()
        val st = Store(ctx)
        assertEquals(0, st.migrate(null))
        waitFile()
        assertTrue(file.exists())
        assertTrue(st.ready)
    }

    @Test fun migratesOldWebData() {
        file.delete()
        val raw = """{"songs":[{"id":"u1","title":"Группа крови","artist":"Кино","capo":"2","text":"[Am]Тёплое место","notes":null,"source":null}],
            "chords":{"квинт":{"frets":[-1,7,9,9,-1,-1],"barre":false}},"settings":{"theme":"ocean","size":20},"clean1":true}"""
        val st = Store(ctx)
        assertEquals(1, st.migrate(raw))
        val s = st.db.songs.single()
        assertEquals("Кино", s.artist); assertEquals(2, s.capo); assertEquals("", s.source); assertEquals(null, s.notes)
        assertEquals("ocean", st.db.settings.theme)
        assertTrue("квинт" in st.db.chords)
        waitFile()
        assertEquals(1, Store(ctx).db.songs.size)
    }
}
