package ru.akkordnik.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import ru.akkordnik.app.music.Book
import java.io.File

class Store(ctx: Context) {
    private val file = File(ctx.filesDir, "akkordnik.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var gen = 0L

    var db by mutableStateOf(Db())
        private set
    /** false, пока идёт перенос данных из прошлой версии приложения */
    var ready by mutableStateOf(false)

    val exists get() = file.exists()

    init {
        if (file.exists()) {
            try { set(dbFromJson(JSONObject(file.readText()))) } catch (_: Exception) {
                File(file.parentFile, "akkordnik.broken.json").let { b -> runCatching { file.copyTo(b, true) } }
            }
            ready = true
        }
    }

    private fun set(d: Db) {
        db = d
        Book.custom = d.chords
        Book.quick = d.settings.quick
        Book.notationH = d.settings.notation == "H"
    }

    fun update(f: (Db) -> Db) {
        set(f(db))
        persist()
    }

    private fun persist() {
        val snapshot = db
        val g = ++gen
        scope.launch {
            lock.withLock {
                if (g != gen) return@withLock
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(snapshot.toJson().toString())
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    /** Данные из localStorage старой (веб) версии. Возвращает число перенесённых песен. */
    fun migrate(raw: String?): Int {
        var n = 0
        try {
            if (!raw.isNullOrBlank()) {
                val o = JSONObject(raw)
                var d = dbFromJson(o)
                if (!o.optBoolean("clean1", false)) {
                    val seedTitles = setOf("Ой, то не вечер", "Чёрный ворон", "Ой, мороз, мороз", "Во поле берёза стояла")
                    d = d.copy(songs = d.songs.filterNot { it.id in setOf("s1", "s2", "s3", "s4") && it.title in seedTitles })
                }
                n = d.songs.size
                if (db.songs.isEmpty() && db.chords.isEmpty()) update { d } else importData(d, replace = false)
            }
        } catch (_: Exception) {}
        // создаём файл, даже если переносить нечего, — чтобы не повторять перенос при каждом запуске
        if (!file.exists()) persist()
        ready = true
        return n
    }

    fun markReady() { ready = true }

    // ---------- импорт файла резервной копии ----------
    fun importData(d: Db, replace: Boolean) = update { cur ->
        if (replace) d
        else {
            val songs = cur.songs.toMutableList()
            d.songs.forEach { s -> val i = songs.indexOfFirst { it.id == s.id }; if (i >= 0) songs[i] = s else songs.add(s) }
            val quick = cur.settings.quick.toMutableList()
            d.settings.quick.forEach { if (it !in quick) quick.add(it) }
            cur.copy(songs = songs, chords = cur.chords + d.chords, settings = cur.settings.copy(quick = quick))
        }
    }
}
