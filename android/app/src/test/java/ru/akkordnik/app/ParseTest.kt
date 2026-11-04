package ru.akkordnik.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.akkordnik.app.music.Lyric
import ru.akkordnik.app.music.canon
import ru.akkordnik.app.music.htmlToSong
import ru.akkordnik.app.music.layoutLyrics
import ru.akkordnik.app.music.normText
import ru.akkordnik.app.music.textToSong
import ru.akkordnik.app.music.transpose
import ru.akkordnik.app.music.voicing

class ParseTest {
    private fun res(n: String) = javaClass.classLoader!!.getResource(n)!!.readText()
    private val ref = JSONObject(res("jsref.json"))

    private fun check(file: String, url: String) {
        val s = htmlToSong(res(file), url)
        val r = ref.getJSONObject(file)
        println("== $file: ${s.title} | ${s.artist} | capo ${s.capo}")
        assertEquals(r.getString("title"), s.title)
        assertEquals(r.getString("artist"), s.artist)
        assertEquals(r.getInt("capo"), s.capo)
        assertEquals(r.getString("text"), s.text)
    }

    @Test fun amdm() = check("amdm_song.html", "https://amdm.ru/akkordi/shapito/219242/malenkim_stal_okean/")
    @Test fun akkordsPro() = check("ak_song.html", "https://akkords.pro/songs/kino/pachka-sigaret/")

    @Test fun paste() {
        val s = textToSong(ref.getString("pasteInput"))
        val r = ref.getJSONObject("paste")
        assertEquals(r.getString("title"), s.title)
        assertEquals(r.getString("artist"), s.artist)
        assertEquals(r.getString("text"), s.text)
    }

    @Test fun music() {
        assertEquals("A#m", canon("Bbm"))
        assertEquals("C#7", transpose("C7", 1))
        assertEquals("A/C#", transpose("G/B", 2))
        assertEquals(listOf(-1, 0, 2, 2, 1, 0), voicing("Am")!!.frets)
        val f = voicing("F")!!
        assertEquals(1, f.barre); assertEquals(listOf(1, 3, 3, 2, 1, 1), f.frets)
        val bm = voicing("Bm")!!
        assertEquals(2, bm.barre); assertEquals(1, bm.barreFrom)
    }

    @Test fun sectionsAndLayout() {
        val t = normText("[Проигрыш]\n[Am]Ой, то не [Dm]вечер\nПрипев:\nAm   C\nслова тут")
        assertTrue(t, t.startsWith("# Проигрыш\n[Am]Ой"))
        assertTrue(t, t.contains("# Припев"))
        val l = layoutLyrics("[Am]Мне малым-мало спало[C]ся")
        val line = l[0] as Lyric.Line
        assertTrue(line.hasChords)
        // «спало[C]ся» — одна неразрывная группа
        assertTrue(line.groups.any { g -> g.size == 2 && g[0].w == "спало" && g[1].c == "C" })
    }
}
