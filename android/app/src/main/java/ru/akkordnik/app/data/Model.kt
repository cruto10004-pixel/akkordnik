package ru.akkordnik.app.data

import org.json.JSONArray
import org.json.JSONObject

val DEF_QUICK = listOf("Am", "C", "D", "Dm", "E", "Em", "F", "G", "A7", "E7")

data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val capo: Int = 0,
    val text: String = "",
    val mode: String = "inline",
    val sel: List<String>? = null,
    val notes: String? = null,
    val prog: String = "",
    val source: String = "",
)

data class ChordDef(val frets: List<Int>?, val barre: Boolean = false, val photo: String? = null)

data class Settings(
    val theme: String = "sunset",
    val size: Int = 18,
    val motion: Boolean = true,
    val notation: String = "B",
    val quick: List<String> = DEF_QUICK,
)

data class Db(
    val songs: List<Song> = emptyList(),
    val chords: Map<String, ChordDef> = emptyMap(),
    val settings: Settings = Settings(),
)

// ---------- JSON (тот же формат, что и у веб-версии) ----------
/** На Android optString() для JSON null возвращает строку "null" — нормализуем в "". */
private fun JSONObject.str(k: String, def: String = ""): String = if (isNull(k)) def else optString(k, def)
private fun JSONArray.str(i: Int): String = if (isNull(i)) "" else optString(i, "")
private fun JSONArray?.strings(): List<String>? = this?.let { a -> (0 until a.length()).map { a.str(it) } }

fun songFromJson(o: JSONObject): Song = Song(
    id = o.str("id").ifEmpty { "u" + System.nanoTime() },
    title = o.str("title"),
    artist = o.str("artist"),
    capo = o.optInt("capo", 0).coerceIn(0, 12),
    text = o.str("text"),
    mode = if (o.str("mode") == "simple") "simple" else "inline",
    sel = o.optJSONArray("sel").strings(),
    notes = if (o.has("notes") && !o.isNull("notes")) o.str("notes") else null,
    prog = o.str("prog"),
    source = o.str("source"),
)

fun Song.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("artist", artist); put("capo", capo); put("text", text)
    put("mode", mode); put("prog", prog)
    sel?.let { put("sel", JSONArray(it)) }
    notes?.let { put("notes", it) }
    put("source", source)
}

fun chordFromJson(o: JSONObject): ChordDef {
    val fa = o.optJSONArray("frets")
    val frets = fa?.let { a -> (0 until a.length()).map { a.optInt(it, -1) } }?.takeIf { it.size == 6 }
    val photo = o.str("photo").takeIf { it.startsWith("data:image") }
    return ChordDef(frets, o.optBoolean("barre", false), photo)
}

fun ChordDef.toJson(): JSONObject = JSONObject().apply {
    frets?.let { put("frets", JSONArray(it)) }
    put("barre", barre)
    photo?.let { put("photo", it) }
}

fun settingsFromJson(o: JSONObject?): Settings {
    val d = Settings()
    if (o == null) return d
    return Settings(
        theme = o.str("theme", d.theme).takeIf { it in THEME_KEYS } ?: d.theme,
        size = o.optInt("size", d.size).coerceIn(14, 34),
        motion = o.optBoolean("motion", d.motion),
        notation = if (o.str("notation") == "H") "H" else "B",
        quick = o.optJSONArray("quick").strings()?.filter { it.isNotBlank() }?.distinct() ?: d.quick,
    )
}

fun Settings.toJson(): JSONObject = JSONObject().apply {
    put("theme", theme); put("size", size); put("motion", motion); put("notation", notation); put("quick", JSONArray(quick))
}

val THEME_KEYS = listOf("sunset", "night", "ocean", "neon", "paper")

fun dbFromJson(o: JSONObject): Db {
    val sa = o.optJSONArray("songs") ?: JSONArray()
    val songs = (0 until sa.length()).mapNotNull { sa.optJSONObject(it)?.let(::songFromJson) }
    val co = o.optJSONObject("chords")
    val chords = LinkedHashMap<String, ChordDef>()
    co?.keys()?.forEach { k -> co.optJSONObject(k)?.let { chords[k] = chordFromJson(it) } }
    return Db(songs, chords, settingsFromJson(o.optJSONObject("settings")))
}

fun Db.toJson(export: Boolean = false): JSONObject = JSONObject().apply {
    if (export) {
        put("app", "akkordnik"); put("version", "1.0")
        put("exported", java.time.Instant.now().toString())
    }
    put("songs", JSONArray(songs.map { it.toJson() }))
    put("chords", JSONObject().apply { chords.forEach { (k, v) -> put(k, v.toJson()) } })
    put("settings", settings.toJson())
    if (!export) put("clean1", true)
}
