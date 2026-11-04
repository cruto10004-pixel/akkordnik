package ru.akkordnik.app.music

import ru.akkordnik.app.data.ChordDef

/** Глобальный справочник: свои аккорды и быстрые аккорды пользователя (обновляется из AppState). */
object Book {
    @Volatile var custom: Map<String, ChordDef> = emptyMap()
    @Volatile var quick: List<String> = emptyList()
    @Volatile var notationH: Boolean = false
}

val NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val FLAT = mapOf(
    "Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#", "H" to "B", "Hb" to "A#",
    "Cb" to "B", "Fb" to "E", "E#" to "F", "B#" to "C"
)
private val CHORD_PARTS = Regex("^([A-H])([#b]?)(.*?)(?:/([A-H][#b]?))?$")

data class ParsedChord(val root: Int, val q: String, val bass: String)

fun parseChord(c: String): ParsedChord? {
    val m = CHORD_PARTS.find(c.trim()) ?: return null
    var r = m.groupValues[1] + m.groupValues[2]
    r = FLAT[r] ?: r
    val root = NOTES.indexOf(r)
    if (root < 0) return null
    return ParsedChord(root, m.groupValues[3], m.groupValues[4])
}

fun noteName(i: Int) = NOTES[((i % 12) + 12) % 12]

fun canon(c: String): String {
    val p = parseChord(c) ?: return c.trim()
    var b = ""
    if (p.bass.isNotEmpty()) parseChord(p.bass)?.let { b = "/" + noteName(it.root) }
    return noteName(p.root) + p.q + b
}

fun transpose(c: String, n: Int): String {
    if (n == 0) return canon(c)
    val p = parseChord(c) ?: return c
    var b = ""
    if (p.bass.isNotEmpty()) parseChord(p.bass)?.let { b = "/" + noteName(it.root + n) }
    return noteName(p.root + n) + p.q + b
}

private val B_RE = Regex("(^|/)B(?!b)")
fun fmt(c: String): String = if (!Book.notationH) c else c.replace(B_RE, "$1H")

private val OPEN = mapOf(
    "C" to listOf(-1, 3, 2, 0, 1, 0), "C7" to listOf(-1, 3, 2, 3, 1, 0), "Cmaj7" to listOf(-1, 3, 2, 0, 0, 0),
    "D" to listOf(-1, -1, 0, 2, 3, 2), "Dm" to listOf(-1, -1, 0, 2, 3, 1), "D7" to listOf(-1, -1, 0, 2, 1, 2),
    "Dm7" to listOf(-1, -1, 0, 2, 1, 1), "Dsus4" to listOf(-1, -1, 0, 2, 3, 3), "Dsus2" to listOf(-1, -1, 0, 2, 3, 0),
    "E" to listOf(0, 2, 2, 1, 0, 0), "Em" to listOf(0, 2, 2, 0, 0, 0), "E7" to listOf(0, 2, 0, 1, 0, 0),
    "Em7" to listOf(0, 2, 2, 0, 3, 0),
    "G" to listOf(3, 2, 0, 0, 0, 3), "G7" to listOf(3, 2, 0, 0, 0, 1),
    "A" to listOf(-1, 0, 2, 2, 2, 0), "Am" to listOf(-1, 0, 2, 2, 1, 0), "A7" to listOf(-1, 0, 2, 0, 2, 0),
    "Am7" to listOf(-1, 0, 2, 0, 1, 0), "Asus4" to listOf(-1, 0, 2, 2, 3, 0), "Asus2" to listOf(-1, 0, 2, 2, 0, 0),
    "Amaj7" to listOf(-1, 0, 2, 1, 2, 0),
    "B7" to listOf(-1, 2, 1, 2, 0, 2)
)
private val SHAPE = mapOf(
    "" to listOf(listOf(0, 2, 2, 1, 0, 0), listOf(-1, 0, 2, 2, 2, 0)),
    "m" to listOf(listOf(0, 2, 2, 0, 0, 0), listOf(-1, 0, 2, 2, 1, 0)),
    "7" to listOf(listOf(0, 2, 0, 1, 0, 0), listOf(-1, 0, 2, 0, 2, 0)),
    "m7" to listOf(listOf(0, 2, 0, 0, 0, 0), listOf(-1, 0, 2, 0, 1, 0)),
    "maj7" to listOf(listOf(0, 2, 1, 1, 0, 0), listOf(-1, 0, 2, 1, 2, 0)),
    "sus4" to listOf(listOf(0, 2, 2, 2, 0, 0), listOf(-1, 0, 2, 2, 3, 0)),
    "sus2" to listOf(listOf(0, 2, 4, 4, 0, 0), listOf(-1, 0, 2, 2, 0, 0))
)

data class Voicing(
    val frets: List<Int>,
    val key: String,
    val barre: Int = 0,
    val barreFrom: Int = 0,
    val custom: Boolean = false,
    val simplified: Boolean = false,
    val photo: String? = null,
)

fun customFor(name: String): ChordDef? = Book.custom[canon(name)] ?: Book.custom[name]

fun barreInfo(frets: List<Int>, on: Boolean): Pair<Int, Int> {
    if (!on) return 0 to 0
    val pl = frets.filter { it > 0 }
    if (pl.isEmpty()) return 0 to 0
    val b = pl.min()
    return b to frets.indexOfFirst { it == b }
}

private val MINOR = Regex("^m(?!aj)")

fun voicing(name: String): Voicing? {
    val cu = customFor(name)
    if (cu?.frets != null) {
        val (b, bf) = barreInfo(cu.frets, cu.barre)
        return Voicing(cu.frets, canon(name), b, bf, custom = true, photo = cu.photo)
    }
    val p = parseChord(name) ?: return null
    val q = p.q
    val known = q in SHAPE
    val base = if (known) q else if (MINOR.containsMatchIn(q)) "m" else ""
    val key = noteName(p.root) + base
    val full = noteName(p.root) + q
    OPEN[full]?.let { return Voicing(it, full, photo = cu?.photo) }
    if (!known) OPEN[key]?.let { return Voicing(it, key, simplified = true, photo = cu?.photo) }
    val fE = ((p.root - 4 + 12) % 12).let { if (it == 0) 12 else it }
    val fA = ((p.root - 9 + 12) % 12).let { if (it == 0) 12 else it }
    val useA = fA < fE
    val f = if (useA) fA else fE
    val shapeKey = if (base == "m" && !known) "m" else if (known) q else base
    val frets = SHAPE.getValue(shapeKey)[if (useA) 1 else 0].map { if (it < 0) -1 else it + f }
    return Voicing(frets, key, f, if (useA) 1 else 0, simplified = !known && q.isNotEmpty(), photo = cu?.photo)
}

/** Простая расстановка пальцев: баррэ = 1, остальные по ладу и струне. null — если пальцев не хватает. */
fun fingers(v: Voicing): IntArray? {
    val res = IntArray(v.frets.size)
    var next = 1
    if (v.barre > 0) {
        v.frets.forEachIndexed { s, f -> if (f == v.barre && s >= v.barreFrom) res[s] = 1 }
        next = 2
    }
    val notes = v.frets.mapIndexed { s, f -> s to f }.filter { (s, f) -> f > 0 && res[s] == 0 }
        .sortedWith(compareBy({ it.second }, { it.first }))
    if (notes.size + next - 1 > 4) return null
    notes.forEach { (s, _) -> res[s] = next++ }
    return res
}

fun bounds(frets: List<Int>): Int {
    val pl = frets.filter { it > 0 }
    val max = (pl + 1).max()
    val min = (pl + 99).min()
    return if (max <= 5) 1 else min
}
