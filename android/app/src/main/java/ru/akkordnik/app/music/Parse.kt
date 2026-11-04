package ru.akkordnik.app.music

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import ru.akkordnik.app.data.Song
import java.net.URI

private const val U = "(?U)"
val CH_RE = Regex("^\\(?\\[?([A-H][#b]?)(maj|min|m|dim|aug|sus|add|M)?(\\d{0,2})(sus\\d|add\\d{1,2}|maj\\d|b5|#5|b9|#9|\\+|°|ø)*(/[A-H][#b]?)?\\]?\\)?$")
private val SECT = Regex(
    "$U^\\s*\\[?\\s*((?:пре-?припев|куплет|припев|вступление|интро|проигрыш|бридж|кода|окончание|концовка|соло|аутро|intro|verse|pre-?chorus|chorus|bridge|outro|solo|interlude)[^\\]:\\n]{0,20}?)\\s*\\]?\\s*:?\\s*(?=\\S|$)(.*)$",
    RegexOption.IGNORE_CASE
)
private val NOISE = Regex(
    "$U^(?:[xх×*]\\s?\\d+|\\d+\\s?[xх×]?|раза?|\\}\\s?[xх×]?\\d*|\\{|\\|+|-+|—|–|\\(|\\)|\\.{2,}|:|/)$",
    RegexOption.IGNORE_CASE
)
private val WS = Regex("$U\\s+")
private val BRACKETS = Regex("[()\\[\\]]")

fun cleanTok(t: String) = t.replace(BRACKETS, "")

fun isChordLine(l: String): Boolean {
    val t = l.trim().split(WS).filter { it.isNotEmpty() }
    if (t.isEmpty()) return false
    return t.any { CH_RE.matches(it) } && t.all { CH_RE.matches(it) || NOISE.matches(it) }
}

fun isChordName(c0: String?): Boolean {
    val c = (c0 ?: "").trim()
    return c.isNotEmpty() && (CH_RE.matches(c) || Book.custom.containsKey(c) || Book.quick.contains(c))
}

private val NONSPACE = Regex("\\S+")

fun mergeLine(cl: String, ll: String): String {
    val pos = NONSPACE.findAll(cl).map { it.range.first to it.value }.toList()
    var s = ll
    val need = pos.maxOf { it.first } + 1
    if (s.length < need) s = s.padEnd(need, ' ')
    for (k in pos.indices.reversed()) {
        val (i, t) = pos[k]
        if (CH_RE.matches(t)) s = s.substring(0, i) + "[" + cleanTok(t) + "]" + s.substring(i)
    }
    return s.trim()
}

fun chordOnly(l: String) = l.trim().split(WS).joinToString(" ") { if (CH_RE.matches(it)) "[" + cleanTok(it) + "]" else it }

private val INLINE_START = Regex("^\\s*\\[[A-H][^\\]]*\\]")
private val NUM_LABEL = Regex("^(\\d+)\\s*[:.)]?\\s*(.*)$")
private val PAREN_NOTE = Regex("\\([^)\\n]{1,20}\\)")
private val MANY_NL = Regex("\n{3,}")

fun textToInline(raw: String): String {
    val lines = raw.replace("\r", "").replace("\t", "    ").replace('\u00a0', ' ').split("\n").map { it.trimEnd() }
    val out = ArrayList<String>()
    var i = 0
    while (i < lines.size) {
        var l = lines[i]
        if (INLINE_START.containsMatchIn(l) && !isChordLine(l)) { out.add(l.trim()); i++; continue }
        val sm = if (!isChordLine(l)) SECT.find(l) else null
        if (sm != null) {
            var lab = sm.groupValues[1].trim()
            l = sm.groupValues[2]
            NUM_LABEL.find(l)?.let { lab += " " + it.groupValues[1]; l = it.groupValues[2] }
            out.add("# $lab")
            if (l.isBlank()) { i++; continue }
        }
        val notes = PAREN_NOTE.findAll(l).map { it.value }.toList()
        if (notes.isNotEmpty() && !isChordLine(l)) {
            val st = l.replace(PAREN_NOTE) { " ".repeat(it.value.length) }
            if (isChordLine(st)) { out.add(notes.joinToString(" ")); l = st }
        }
        if (isChordLine(l)) {
            val nx = lines.getOrNull(i + 1)
            if (nx != null && nx.isNotBlank() && !isChordLine(nx) && !SECT.containsMatchIn(nx)) {
                out.add(mergeLine(l, nx)); i++
            } else out.add(chordOnly(l))
        } else out.add(if (l.isNotBlank()) l.trimStart() else "")
        i++
    }
    return out.joinToString("\n").replace(MANY_NL, "\n\n").trim()
}

private val BRACKET_ONLY = Regex("^\\s*\\[([^\\]]+)\\]\\s*:?\\s*$")
private val HASH_START = Regex("^\\s*#")

/** Нормализация: [Раздел] и «Припев:» → «# Раздел», аккорды строкой над словами → внутрь текста. */
fun normText(t0: String?): String {
    val t = (t0 ?: "").split("\n").joinToString("\n") { l ->
        val m = BRACKET_ONLY.find(l)
        if (m != null && !isChordName(m.groupValues[1])) return@joinToString "# " + m.groupValues[1].trim().removeSuffix(":")
        val sm = if (!HASH_START.containsMatchIn(l) && !isChordLine(l)) SECT.find(l) else null
        if (sm != null && sm.groupValues[2].isBlank()) "# " + sm.groupValues[1].trim() else l
    }
    return if (t.split("\n").any { isChordLine(it) }) textToInline(t) else t
}

private val BR_CHORD = Regex("\\[([^\\]]+)\\]")

fun chordsOf(text: String): List<String> {
    val set = ArrayList<String>()
    BR_CHORD.findAll(text).forEach {
        val c = it.groupValues[1].trim()
        if (isChordName(c) && c !in set) set.add(c)
    }
    return set
}

// ---------- старый формат «prog» (режим «Сверху отдельно») ----------
data class ProgItem(val c: String? = null, val t: String? = null)
data class ProgLine(val label: String, val items: List<ProgItem>)

private val LABEL_RE = Regex("^([^:]{1,30}):\\s*(.*)$")
private val SEP_RE = Regex("[\\s,;|]")

fun parseProg(prog: String?): List<ProgLine> {
    val known = (Book.custom.keys + Book.quick).toSet().filter { !CH_RE.matches(it) }.sortedByDescending { it.length }
    return (prog ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
        var label = ""
        var rest = l
        LABEL_RE.find(l)?.let { if (!CH_RE.matches(it.groupValues[1].trim())) { label = it.groupValues[1].trim(); rest = it.groupValues[2] } }
        val items = ArrayList<ProgItem>()
        var i = 0
        val low = rest.lowercase()
        while (i < rest.length) {
            if (rest[i].isWhitespace()) { i++; continue }
            val k = known.firstOrNull { n ->
                low.startsWith(n.lowercase(), i) && (i + n.length >= rest.length || SEP_RE.matches(rest[i + n.length].toString()))
            }
            if (k != null) { items.add(ProgItem(c = k)); i += k.length; continue }
            val t = NONSPACE.find(rest, i)!!.value
            items.add(if (CH_RE.matches(t)) ProgItem(c = cleanTok(t)) else ProgItem(t = t))
            i += t.length
        }
        ProgLine(label, items)
    }
}

fun simpleSel(s: Song): List<String> {
    s.sel?.let { return it }
    val out = ArrayList<String>()
    parseProg(s.prog).forEach { l -> l.items.forEach { it.c?.let { c -> if (c !in out) out.add(c) } } }
    return out
}

fun simpleNotes(s: Song): String {
    s.notes?.let { return it }
    return parseProg(s.prog).filter { l -> l.items.none { it.c != null } }
        .joinToString("\n") { l -> (if (l.label.isNotEmpty()) l.label + ": " else "") + l.items.joinToString(" ") { it.t ?: "" } }
}

fun songChords(s: Song, tr: Int = 0): List<String> {
    val set = ArrayList<String>()
    fun add(c0: String) { val c = transpose(c0, tr); if (c !in set) set.add(c) }
    if (s.mode == "simple") simpleSel(s).forEach(::add)
    chordsOf(normText(s.text)).forEach(::add)
    return set
}

// ---------- разметка текста для экрана песни ----------
data class Seg(val c: String?, val w: String)
sealed interface Lyric {
    data class Section(val label: String) : Lyric
    data object Blank : Lyric
    data class Line(val groups: List<List<Seg>>, val hasChords: Boolean) : Lyric
}

private val TOKENS = Regex("\\s*\\S+\\s*")
private val SEC_PREFIX = Regex("^#\\s*")

fun layoutLyrics(text: String): List<Lyric> = normText(text).split("\n").map { line ->
    when {
        line.startsWith("#") -> Lyric.Section(line.replace(SEC_PREFIX, ""))
        line.isBlank() -> Lyric.Blank
        else -> {
            var segs = ArrayList<Seg>()
            var last = 0
            var cur: String? = null
            BR_CHORD.findAll(line).forEach { m ->
                if (!isChordName(m.groupValues[1])) return@forEach
                segs.add(Seg(cur, line.substring(last, m.range.first)))
                cur = m.groupValues[1].trim()
                last = m.range.last + 1
            }
            segs.add(Seg(cur, line.substring(last)))
            // отступ в начале строки с текстом не нужен: аккорд встаёт над первым слогом
            if (segs.any { it.w.isNotBlank() }) {
                var lead = true
                segs = ArrayList(segs.map { sg ->
                    if (!lead) sg else { val t = sg.w.trimStart(); if (t.isNotEmpty()) lead = false; Seg(sg.c, t) }
                })
            }
            segs = ArrayList(segs.filter { it.c != null || it.w.isNotEmpty() })
            val split = segs.flatMap { sg ->
                val tk = TOKENS.findAll(sg.w).map { it.value }.toList().ifEmpty { listOf(sg.w) }
                tk.mapIndexed { k, w -> Seg(if (k == 0) sg.c else null, w) }
            }
            val groups = ArrayList<ArrayList<Seg>>()
            split.forEach { sg ->
                val g = groups.lastOrNull()
                if (g != null && !g.last().w.endsWithWs() && !sg.w.startsWithWs() && sg.w.isNotEmpty()) g.add(sg)
                else groups.add(arrayListOf(sg))
            }
            Lyric.Line(groups, split.any { it.c != null })
        }
    }
}

private fun String.endsWithWs() = isNotEmpty() && last().isWhitespace()
private fun String.startsWithWs() = isNotEmpty() && first().isWhitespace()

// ---------- импорт ----------
private val CAPO_RE = Regex("$U(?:капо(?:дастр)?|capo)[^\\d\\n]{0,25}?(\\d{1,2})", RegexOption.IGNORE_CASE)
fun findCapo(t: String?): Int {
    val n = CAPO_RE.find(t ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return if (n in 1..12) n else 0
}

private val TITLE_TAIL = Regex("$U\\s*[:,|\\-–—]?\\s*(аккорды|подбор|табы|разбор|текст и аккорды|chords|tabs)\\b.*$", RegexOption.IGNORE_CASE)
fun cleanTitle(t: String?): String = (t ?: "").replace(WS, " ").replace(TITLE_TAIL, "").trim()

private val DASH_SPLIT = Regex("^(.+?)\\s+[-–—]\\s+(.+)$")
private val ARTIST_PAREN = Regex("\\s*\\([^)]*\\)\\s*$")
private val NAV_WORDS = Regex("$U(?:песни|аккорды|главная|songs|исполнители)", RegexOption.IGNORE_CASE)

class NoChordsException : Exception("nochords")

fun htmlToSong(html: String, url: String): Song = htmlToSong(Jsoup.parse(html, url), url)

fun htmlToSong(doc: Document, url: String): Song {
    val jsonld = doc.select("script[type=application/ld+json]").map { it.data() }
    doc.select("script,style,noscript,svg,iframe,form,#comments,.comments,.comment").remove()
    val cands = doc.select("pre[itemprop=chordsBlock],.podbor__text,p.chords,div.chords,pre,.song-text,#song_text,.chords-text,[class*=chord-text],[class*=song_text],[class*=songtext],[itemprop=text]")
    var best: String? = null
    var score = 0
    for (el in cands) {
        el.select("br").forEach { it.replaceWith(TextNode("\n")) }
        el.select("div,p").forEach { d -> if (d.closest("pre,p.chords") != null && d.className().contains("podbor__keyword")) d.appendText("\n") }
        val t = el.wholeText()
        val sc = t.split("\n").count { isChordLine(it) }
        if (sc > score) { score = sc; best = t }
    }
    if (best == null || score < 2) throw NoChordsException()
    val h1 = doc.selectFirst("h1")
    var title = h1?.selectFirst("[itemprop=name]")?.text()?.trim() ?: ""
    var artist = h1?.selectFirst("[itemprop=byArtist]")?.text()?.trim() ?: ""
    if (title.isEmpty() || artist.isEmpty()) {
        for (sc in jsonld) {
            try {
                fun walk(o: Any?) {
                    when (o) {
                        is JSONArray -> for (k in 0 until o.length()) walk(o.opt(k))
                        is JSONObject -> {
                            val ty = when (val t = o.opt("@type")) { is JSONArray -> (0 until t.length()).map { t.optString(it) }; null -> emptyList(); else -> listOf(t.toString()) }
                            if ("MusicRecording" in ty || "MusicComposition" in ty) {
                                if (title.isEmpty() && o.optString("name").isNotEmpty()) title = o.optString("name")
                                val ba = o.opt("byArtist")
                                if (artist.isEmpty() && ba != null) {
                                    val a = if (ba is JSONArray) ba.opt(0) else ba
                                    artist = (a as? JSONObject)?.optString("name") ?: ""
                                }
                            }
                            if (o.has("@graph")) walk(o.opt("@graph"))
                        }
                    }
                }
                val s = sc.trim()
                walk(if (s.startsWith("[")) JSONArray(s) else JSONObject(s))
            } catch (_: Exception) {}
        }
    }
    artist = artist.replace(ARTIST_PAREN, "")
    if (title.isEmpty()) {
        val og = doc.selectFirst("meta[property=og:title]")?.attr("content")
        val raw = cleanTitle(h1?.text() ?: og ?: doc.title())
        val m = DASH_SPLIT.find(raw)
        if (m != null) { artist = m.groupValues[1]; title = m.groupValues[2] } else title = raw
    }
    if (artist.isEmpty()) {
        try {
            val seg = URI(url).path.split("/").filter { it.isNotEmpty() }
            for (sg in seg.dropLast(1)) {
                val a = doc.select("a[href*=/$sg/]").map { it.text().trim() }
                    .firstOrNull { it.isNotEmpty() && it.length < 40 && !NAV_WORDS.containsMatchIn(it) }
                if (a != null) { artist = a; break }
            }
        } catch (_: Exception) {}
    }
    val meta = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
    val capo = findCapo(best).takeIf { it > 0 } ?: findCapo(h1?.text()).takeIf { it > 0 } ?: findCapo(meta)
    return Song(
        id = "", title = cleanTitle(title).ifEmpty { "Без названия" }, artist = artist.replace(WS, " ").trim(),
        capo = capo, text = textToInline(best!!), mode = "inline", source = url
    )
}

private val HAS_CHORD = Regex("\\[[A-H]")

fun textToSong(raw: String): Song {
    val lines = raw.trim().split("\n").toMutableList()
    var title = ""
    var artist = ""
    val first = lines.firstOrNull()?.trim()
    if (!first.isNullOrEmpty() && !isChordLine(first) && !SECT.containsMatchIn(first) && first.length < 80 && lines.size > 3) {
        val m = DASH_SPLIT.find(cleanTitle(first))
        if (m != null) { artist = m.groupValues[1]; title = m.groupValues[2] } else title = cleanTitle(first)
        lines.removeAt(0)
    }
    val text = textToInline(lines.joinToString("\n"))
    if (!HAS_CHORD.containsMatchIn(text)) throw NoChordsException()
    return Song(id = "", title = title.ifEmpty { "Новая песня" }, artist = artist, capo = findCapo(raw), text = text, mode = "inline")
}

fun hostOf(u: String): String = try { URI(u).host?.removePrefix("www.") ?: u } catch (_: Exception) { u }

const val MOB_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

fun fetchSong(url: String): Song {
    val res = Jsoup.connect(url).userAgent(MOB_UA)
        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
        .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
        .timeout(20000).maxBodySize(0).followRedirects(true).execute()
    return htmlToSong(res.parse(), url)
}
