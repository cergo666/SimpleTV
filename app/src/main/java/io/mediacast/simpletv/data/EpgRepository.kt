package io.mediacast.simpletv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import androidx.core.database.sqlite.transaction
import io.mediacast.simpletv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import java.util.Collections
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Телепрограмма XMLTV. Как в SimpleTV: несколько источников, ETag/Last-Modified
 * (checkHttpFiles), сопоставление каналов по tvg-id и по всем display-name (useNameSynonyms),
 * очистка старых записей (clearDays=7).
 */
class EpgRepository(private val context: Context, private val settings: Settings) : EpgManager {

    private val dbFile = File(context.filesDir, "epg.db")
    private val cacheDir = File(context.filesDir, "epg_cache").apply { mkdirs() }
    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var names: Map<String, String> = emptyMap()
    @Volatile private var loose: Map<String, String> = emptyMap()
    @Volatile private var ids: Map<String, String> = emptyMap() // сырой id → id в базе
    @Volatile private var stored: Set<String> = emptySet()
    /** Читатели курсоров и пересборка/смена файла базы сериализуются этим замком. */
    private val dbLock = Any()
    private val icons = ConcurrentHashMap<String, String>()
    private val resolved = ConcurrentHashMap<String, String>()

    // ConcurrentHashMap.newKeySet() requires API 24, so keep a set view of a map instead.
    private val noMatch: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    init {
        open()
    }

    private fun open() {
        db?.close()
        db = null
        names = emptyMap(); loose = emptyMap(); ids = emptyMap()
        icons.clear(); resolved.clear(); noMatch.clear()
        if (!dbFile.exists()) return
        val d = runCatching { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull() ?: return
        val n = HashMap<String, String>()
        val l = HashMap<String, String>()
        val i = HashMap<String, String>()
        val st = HashSet<String>()
        d.rawQuery("SELECT k, id FROM names ORDER BY rowid", null).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(0); val id = c.getString(1)
                if (!n.containsKey(k)) n[k] = id
            }
        }
        d.rawQuery("SELECT k, id FROM loose ORDER BY rowid", null).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(0)
                if (!l.containsKey(k)) l[k] = c.getString(1)
            }
        }
        d.rawQuery("SELECT id, raw, icon, has FROM ch", null).use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) st += c.getString(0)
                val raw = c.getString(1)
                if (!i.containsKey(raw)) i[raw] = c.getString(0)
                c.getString(2)?.let { icons[c.getString(0)] = it }
            }
        }
        names = n; loose = l; ids = i; stored = st
        db = d
    }

    override val isEmpty: Boolean get() = db == null

    // ------------------------------------------------------------------ сопоставление каналов

    override fun epgIdFor(ch: Channel): String? {
        resolved[ch.key]?.let { return it }
        if (ch.key in noMatch) return null
        val id = ch.tvgId?.let { ids[it] }
            ?: ch.tvgName?.let { names[Text.channelKey(it)] }
            ?: names[ch.key]
            ?: loose[Text.looseKey(ch.name)]
        if (id != null) resolved[ch.key] = id else noMatch += ch.key
        return id
    }

    override fun iconFor(ch: Channel): String? = epgIdFor(ch)?.let { icons[it] }

    override fun nowNext(ch: Channel, now: Long): List<Programme> {
        val id = epgIdFor(ch) ?: return emptyList()
        return query("SELECT s, e, t, d, c FROM pr WHERE ch = ? AND e > ? ORDER BY s LIMIT 2", arrayOf(id, now.toString()))
    }

    override fun schedule(ch: Channel, from: Long, to: Long): List<Programme> {
        val id = epgIdFor(ch) ?: return emptyList()
        return query("SELECT s, e, t, d, c FROM pr WHERE ch = ? AND e > ? AND s < ? ORDER BY s", arrayOf(id, from.toString(), to.toString()))
    }

    fun guideMatches(channels: List<Channel>, query: String, now: Long): Map<String, Programme> = synchronized(dbLock) {
        val db = db ?: return emptyMap()
        val byId = channels.mapNotNull { ch -> epgIdFor(ch)?.let { it to ch.key } }
            .groupBy({ it.first }, { it.second })
        val result = HashMap<String, Programme>()
        val args = if (query.isBlank()) arrayOf(now.toString(), now.toString())
            else arrayOf((now - 3 * 86_400_000L).toString(), (now + 3 * 86_400_000L).toString())
        return runCatching {
            db.rawQuery("SELECT ch, s, e, t FROM pr WHERE e > ? AND s <= ? ORDER BY s", args).use { cursor ->
                while (cursor.moveToNext()) {
                    val keys = byId[cursor.getString(0)] ?: continue
                    val title = cursor.getString(3) ?: continue
                    if (query.isNotBlank() && !title.contains(query, ignoreCase = true)) continue
                    val item = Programme(cursor.getLong(1), cursor.getLong(2), title, null, null)
                    for (key in keys) if (!result.containsKey(key)) result[key] = item
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun query(sql: String, args: Array<String>): List<Programme> = synchronized(dbLock) {
        val d = db ?: return emptyList()
        return runCatching {
            d.rawQuery(sql, args).use { c ->
                val out = ArrayList<Programme>(c.count)
                while (c.moveToNext()) out += Programme(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3), c.getString(4))
                out
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ обновление

    /** @return true, если база перестроена. */
    override suspend fun update(force: Boolean, channels: List<Channel>, progress: EpgManager.Progress): Boolean = withContext(Dispatchers.IO) {
        val sources = settings.epgSources.filter { it.enabled && it.url.isNotBlank() }
        if (sources.isEmpty()) {
            settings.epgStatus = context.getString(R.string.epg_no_sources)
            return@withContext synchronized(dbLock) {
                val hadGuide = db != null
                db?.close()
                db = null
                dbFile.delete()
                open()
                settings.appliedEpgSources = null
                hadGuide
            }
        }
        val sourceKey = org.json.JSONArray(sources.map { it.toJson() }).toString()
        var changed = force || db == null || sourceKey != settings.appliedEpgSources
        val files = ArrayList<Pair<EpgSource, File>>()
        val status = StringBuilder()
        for (src in sources) {
            val file = File(cacheDir, "${src.id}.bin")
            progress.report(context.getString(R.string.epg_loading, context.epgSourceLabel(src)))
            val useCache = !force && file.exists()
            val r = runCatching {
                Http.open(
                    src.url, settings.epgUserAgent,
                    etag = if (useCache) settings.epgEtag(src.id) else null,
                    lastModified = if (useCache) settings.epgLastModified(src.id) else null,
                    readTimeoutMs = 60000,
                    gunzip = false,
                ).use { resp ->
                    when {
                        resp.notModified -> context.getString(R.string.epg_unchanged)
                        resp.ok -> {
                            val tmp = File(cacheDir, "${src.id}.tmp")
                            try {
                                val body = resp.body ?: throw IllegalStateException(context.getString(R.string.epg_empty_response))
                                tmp.outputStream().use { out ->
                                    val buffer = ByteArray(256 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val read = body.read(buffer)
                                        if (read < 0) break
                                        out.write(buffer, 0, read)
                                        total += read
                                        if (total % (8L shl 20) < read) {
                                            progress.report(context.getString(R.string.epg_downloaded_mb, context.epgSourceLabel(src), total shr 20))
                                        }
                                    }
                                }
                                validateXmltv(tmp)
                                if (!tmp.renameTo(file)) {
                                    throw java.io.IOException(context.getString(R.string.epg_error_status, src.name))
                                }
                            } finally {
                                tmp.delete()
                            }
                            settings.setEpgCache(src.id, resp.etag, resp.lastModified)
                            changed = true
                            context.getString(R.string.epg_updated_short)
                        }
                        else -> throw IllegalStateException("HTTP ${resp.code}")
                    }
                }
            }
            status.append(context.epgSourceLabel(src)).append(": ").append(r.getOrElse { context.getString(R.string.epg_error_status, it.message ?: "") }).append("; ")
            if (file.exists()) files += src to file
        }
        if (files.isEmpty()) {
            settings.epgStatus = status.toString().trimEnd(' ', ';')
            return@withContext false
        }
        if (!changed) {
            settings.lastEpgUpdate = System.currentTimeMillis()
            settings.epgStatus = status.toString().trimEnd(' ', ';')
            return@withContext false
        }
        val count = rebuild(files, channels, progress)
        settings.appliedEpgSources = sourceKey
        settings.lastEpgUpdate = System.currentTimeMillis()
        settings.epgStatus = status.append(context.getString(R.string.epg_count_prefix)).append(count).toString()
        true
    }

    private fun validateXmltv(file: File) {
        FileInputStream(file).use { raw ->
            val parser = Xml.newPullParser()
            parser.setInput(Http.maybeGunzip(raw), null)
            require(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "tv") {
                "Expected an XMLTV document"
            }
            var hasProgrammes = false
            var closedRoot = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.END_TAG && parser.depth == 1 && parser.name == "tv") {
                    closedRoot = true
                }
                if (!hasProgrammes && parser.eventType == XmlPullParser.START_TAG &&
                    parser.depth == 2 && parser.name == "programme"
                ) {
                    val start = parseTime(parser.getAttributeValue(null, "start"))
                    val stop = parseTime(parser.getAttributeValue(null, "stop"))
                    hasProgrammes = start > 0 && stop > start
                }
            }
            require(closedRoot) { "Incomplete XMLTV document" }
            require(hasProgrammes) { "XMLTV contains no valid programmes" }
        }
    }

    /**
     * После обновления плейлиста: появились каналы, для которых в XMLTV есть программа,
     * но она не сохранена (храним только каналы плейлиста) — надо перестроить базу из кэша.
     */
    override fun needsRelink(channels: List<Channel>): Boolean =
        db != null && channels.any { ch -> epgIdFor(ch)?.let { it !in stored } == true }

    override suspend fun relink(channels: List<Channel>, progress: EpgManager.Progress): Boolean = withContext(Dispatchers.IO) {
        val files = settings.epgSources.filter { it.enabled }.mapNotNull { src ->
            File(cacheDir, "${src.id}.bin").takeIf { it.exists() }?.let { src to it }
        }
        if (files.isEmpty()) return@withContext false
        rebuild(files, channels, progress)
        true
    }

    private fun rebuild(files: List<Pair<EpgSource, File>>, channels: List<Channel>, progress: EpgManager.Progress): Int {
        // Сохраняем программу только для каналов плейлиста (иначе база ~200 МБ)
        val keys = HashSet<String>(); val looseKeys = HashSet<String>(); val tvgIds = HashSet<String>()
        for (ch in channels) {
            keys += ch.key; looseKeys += Text.looseKey(ch.name)
            ch.tvgName?.let { keys += Text.channelKey(it) }
            ch.tvgId?.let { tvgIds += it }
        }
        val filter = channels.isNotEmpty()
        val matched = HashSet<String>()
        val newFile = File(context.filesDir, "epg_new.db")
        newFile.delete(); File(newFile.path + "-journal").delete()
        val d = SQLiteDatabase.openOrCreateDatabase(newFile, null)
        try {
        // PRAGMA возвращают строку результата — через execSQL на Android нельзя
        d.rawQuery("PRAGMA journal_mode=OFF", null).use { it.moveToFirst() }
        d.rawQuery("PRAGMA synchronous=OFF", null).use { it.moveToFirst() }
        d.execSQL("CREATE TABLE ch (id TEXT PRIMARY KEY, raw TEXT, icon TEXT, has INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE names (k TEXT, id TEXT)")
        d.execSQL("CREATE TABLE loose (k TEXT, id TEXT)")
        d.execSQL("CREATE TABLE pr (ch TEXT, s INTEGER, e INTEGER, t TEXT, d TEXT, c TEXT)")
        val now = System.currentTimeMillis()
        val minStop = now - 5L * 86400_000   // глубина архива Wink/Beeline — 3–4 дня
        val maxStart = now + 8L * 86400_000
        var total = 0
        var parsedSources = 0
        val insCh = d.compileStatement("INSERT OR IGNORE INTO ch (id, raw, icon, has) VALUES (?,?,?,?)")
        val insName = d.compileStatement("INSERT INTO names VALUES (?,?)")
        val insLoose = d.compileStatement("INSERT INTO loose VALUES (?,?)")
        val insPr = d.compileStatement("INSERT INTO pr VALUES (?,?,?,?,?,?)")
        for ((src, file) in files) {
            progress.report(context.getString(R.string.epg_parsing, context.epgSourceLabel(src)))
            try {
            var sourceCount = 0
            d.transaction {
                FileInputStream(file).use { raw ->
                    val input = Http.maybeGunzip(raw)
                    val p = Xml.newPullParser()
                    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    p.setInput(input, null)
                    var ev = p.nextTag()
                    require(ev == XmlPullParser.START_TAG && p.name == "tv") { "Expected an XMLTV document" }
                    var n = 0
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.START_TAG) when (p.name) {
                            "channel" -> {
                                val rawId = p.getAttributeValue(null, "id") ?: ""
                                val id = "${src.id}:$rawId"
                                var icon: String? = null
                                val dn = ArrayList<String>()
                                readChildren(p, "channel") { tag ->
                                    when (tag) {
                                        "display-name" -> dn += text(p)
                                        "icon" -> icon = p.getAttributeValue(null, "src")
                                    }
                                }
                                insCh.bindString(1, id); insCh.bindString(2, rawId)
                                icon?.let { insCh.bindString(3, it) } ?: insCh.bindNull(3)
                                val want = !filter || rawId in tvgIds ||
                                    dn.any { Text.channelKey(it) in keys || Text.looseKey(it) in looseKeys }
                                if (want) matched += id
                                insCh.bindLong(4, if (want) 1 else 0)
                                insCh.executeInsert()
                                for (name in dn) {
                                    insName.bindString(1, Text.channelKey(name)); insName.bindString(2, id); insName.executeInsert()
                                    insLoose.bindString(1, Text.looseKey(name)); insLoose.bindString(2, id); insLoose.executeInsert()
                                }
                            }
                            "programme" -> if (filter && "${src.id}:${p.getAttributeValue(null, "channel")}" !in matched) {
                                skip(p)
                            } else {
                                val s = parseTime(p.getAttributeValue(null, "start"))
                                val e = parseTime(p.getAttributeValue(null, "stop"))
                                val ch = p.getAttributeValue(null, "channel")
                                var title: String? = null
                                var desc: String? = null
                                var cat: String? = null
                                readChildren(p, "programme") { tag ->
                                    when (tag) {
                                        "title" -> if (title == null) title = text(p)
                                        "sub-title" -> { val st = text(p); if (st.isNotBlank()) title = (title ?: "") + ". " + st }
                                        "desc" -> if (desc == null) desc = text(p)
                                        "category" -> if (cat == null) cat = text(p)
                                    }
                                }
                                if (ch != null && s > 0 && e > s && e >= minStop && s <= maxStart) {
                                    insPr.bindString(1, "${src.id}:$ch"); insPr.bindLong(2, s); insPr.bindLong(3, e)
                                    insPr.bindString(4, title ?: "")
                                    desc?.let { insPr.bindString(5, it.take(600)) } ?: insPr.bindNull(5)
                                    cat?.let { insPr.bindString(6, it) } ?: insPr.bindNull(6)
                                    insPr.executeInsert()
                                    sourceCount++
                                    if (++n % 20000 == 0) progress.report(context.resources.getQuantityString(R.plurals.epg_parsed_count, n, context.epgSourceLabel(src), n))
                                }
                            }
                        }
                        ev = p.next()
                    }
                }
            }
            total += sourceCount
            parsedSources++
            } catch (e: Exception) {
                matched.removeAll { it.startsWith("${src.id}:") }
                progress.report(context.getString(R.string.epg_parse_error, context.epgSourceLabel(src), e.message ?: ""))
            }
        }
        check(parsedSources > 0) { "No XMLTV source could be parsed" }
        progress.report(context.getString(R.string.epg_indexing))
        d.execSQL("CREATE INDEX pr_idx ON pr (ch, s)")
        d.execSQL("CREATE INDEX names_idx ON names (k)")
        d.close()
        synchronized(dbLock) {
            db?.close(); db = null
            if (!newFile.renameTo(dbFile)) {
                open()
                throw java.io.IOException("Failed to replace EPG database")
            }
            open()
        }
        return total
        } catch (e: Exception) {
            runCatching { d.close() }
            newFile.delete()
            throw e
        }
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Обходит дочерние теги до закрывающего [tag]. */
    private inline fun readChildren(p: XmlPullParser, tag: String, onTag: (String) -> Unit) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    val name = p.name
                    onTag(name)
                    // text() переводит парсер на END_TAG текущего элемента
                    if (p.eventType == XmlPullParser.START_TAG) depth++
                }
                XmlPullParser.END_TAG -> if (p.name == tag && depth == 1) depth = 0 else depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Как nextText(), но не падает на вложенной разметке внутри <desc>. */
    private fun text(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
    }

    override fun clear() {
        synchronized(dbLock) {
            db?.close(); db = null
            dbFile.delete()
            cacheDir.listFiles()?.forEach { it.delete() }
            settings.clearEpgCache()
            settings.appliedEpgSources = null
            open()
        }
    }

    companion object {
        private val utc = TimeZone.getTimeZone("UTC")

        /** "20261026030000 +0300" → unix ms */
        fun parseTime(s: String?): Long {
            if (s == null || s.length < 14) return 0
            return try {
                val cal = Calendar.getInstance(utc)
                cal.clear()
                cal.set(
                    s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(),
                    s.substring(8, 10).toInt(), s.substring(10, 12).toInt(), s.substring(12, 14).toInt()
                )
                var ms = cal.timeInMillis
                // "+0300" и "+03:00" — оба встречаются в XMLTV.
                val tz = s.substring(14).trim().replace(":", "")
                if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                    val off = (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt()) * 60_000L
                    ms -= if (tz[0] == '+') off else -off
                }
                ms
            } catch (e: Exception) {
                0
            }
        }
    }
}
