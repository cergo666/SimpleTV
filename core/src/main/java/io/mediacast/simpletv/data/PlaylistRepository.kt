package io.mediacast.simpletv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Аналог TVSources: загружает источники, фильтрует и проверяет потоки,
 * объединяет одинаковые каналы разных источников в один канал с несколькими вариантами.
 */
open class PlaylistRepository(
    private val dataDir: File,
    private val settings: Settings,
    var contentStreamResolver: ((String) -> InputStream?)? = null,
    var assetStreamResolver: ((String) -> InputStream?)? = null,
) {
    private val dir = File(dataDir, "sources").apply { mkdirs() }
    private val bundled = ConcurrentHashMap<SourceConfig, List<SourceChannel>>()
    private val cultureHdSources = setOf("beeline", "smotrim", "vitrina")

    private fun isBundled(source: SourceConfig): Boolean =
        source.builtIn && source.urls.any { it.startsWith("asset://") }

    private fun onlineUrls(source: SourceConfig) = source.urls.filterNot { it.startsWith("asset://") }

    private fun onlineCacheId(source: SourceConfig) = "${source.id}.online"

    fun needsBundledUpdate(): Boolean =
        settings.sources.any {
            it.enabled && isBundled(it) && onlineUrls(it).isNotEmpty() &&
                !File(dir, "${onlineCacheId(it)}.json").exists()
        }

    fun interface Progress {
        fun report(text: String)
    }

    /** Обновить все включённые источники (кнопка «Обновить плейлист»). */
    suspend fun refreshAll(
        progress: Progress,
        onPartial: suspend (List<Channel>) -> Unit = {},
    ): List<Channel> =
        withContext(Dispatchers.IO) {
            val report = StringBuilder()
            for (src in settings.sources.filter { it.enabled }) {
                progress.report(Messages[Messages.Key.PLAYLIST_LOADING, src.name])
                val r = runCatching { refreshSource(src, progress) }
                r.onSuccess { list ->
                    onPartial(loadMerged())
                    report.append("${src.name}: ${list.size}; ")
                    progress.report(Messages[Messages.Key.PLAYLIST_CHANNELS, src.name, list.size])
                }.onFailure { e ->
                    report.append("${src.name}: ${Messages[Messages.Key.PLAYLIST_STATUS_ERROR]} (${e.message}); ")
                    progress.report(Messages[Messages.Key.PLAYLIST_ERROR, src.name, e.message ?: ""])
                }
            }
            settings.lastPlaylistUpdate = System.currentTimeMillis()
            settings.playlistStatus = report.toString().trimEnd(' ', ';')
            loadMerged()
        }

    suspend fun refreshOne(
        src: SourceConfig,
        progress: Progress,
    ) =
        withContext(Dispatchers.IO) {
            refreshSource(src, progress)
            Unit
        }

    private suspend fun refreshSource(
        src: SourceConfig,
        progress: Progress,
    ): List<SourceChannel> {
        if (!isBundled(src)) {
            return loadSource(src, progress).also { save(src.id, it) }
        }
        val base = loadBundled(src)
        val urls = onlineUrls(src)
        if (urls.isEmpty()) return base
        val updates = loadSource(src.copy(urls = urls), progress)
        check(updates.isNotEmpty()) { Messages[Messages.Key.PLAYLIST_UNAVAILABLE] }
        save(onlineCacheId(src), updates)
        return mergeBundled(base, updates)
    }

    private suspend fun loadBundled(src: SourceConfig): List<SourceChannel> =
        bundled[src] ?: runCatching {
            val assets = src.urls.filter { it.startsWith("asset://") }
            val parsed = loadSource(src.copy(urls = assets, checkStreams = false)) {}
            bundled.putIfAbsent(src, parsed) ?: parsed
        }.getOrDefault(emptyList())

    private fun mergeBundled(
        base: List<SourceChannel>,
        updates: List<SourceChannel>,
    ): List<SourceChannel> {
        val merged = base.associateByTo(LinkedHashMap()) { it.variant.url }
        val cineramaMetadata =
            base.mapNotNull { channel ->
                PublicTv.cineramaChannelId(channel.variant.url)?.let { it to channel }
            }.toMap()
        for (update in updates) {
            val variant = update.variant
            val existing = merged[variant.url] ?: cineramaMetadata[PublicTv.cineramaChannelId(variant.url)]
            merged[variant.url] =
                if (existing == null) {
                    update
                } else {
                    existing.copy(
                        logo = existing.logo ?: update.logo,
                        tvgId = existing.tvgId ?: update.tvgId,
                        tvgName = existing.tvgName ?: update.tvgName,
                        variant =
                            variant.copy(
                                clearKey = variant.clearKey ?: existing.variant.clearKey,
                                catchup = variant.catchup ?: existing.variant.catchup,
                            ),
                    )
                }
        }
        return merged.values.toList()
    }

    private suspend fun loadSource(
        src: SourceConfig,
        progress: Progress,
    ): List<SourceChannel> {
        val skipWeb = settings.skipWebPages
        val all = ArrayList<SourceChannel>()
        var okLists = 0
        src.urls.forEachIndexed { i, url ->
            if (src.urls.size > 1) progress.report(Messages[Messages.Key.PLAYLIST_N_OF_M, src.name, i + 1, src.urls.size])
            val text =
                runCatching {
                    val content = readPlaylist(url, src.playlistUserAgent)
                    if (src.builtIn && url.startsWith("https://")) PublicTv.catalog(src.id, content) else content
                }.getOrNull() ?: return@forEachIndexed
            if (!text.trimStart().startsWith("#EXTM3U")) return@forEachIndexed
            okLists++
            all += M3uParser.parse(text, src, skipWeb).channels
        }
        if (okLists == 0) throw IllegalStateException(Messages[Messages.Key.PLAYLIST_UNAVAILABLE])
        // Дубли по адресу убираем всегда (RemoveDupCH / SkipDuplicate)
        val unique = all.distinctBy { it.variant.url }
        if (!src.checkStreams) return unique
        return checkStreams(src, unique, progress)
    }

    private fun readPlaylist(
        url: String,
        ua: String?,
    ): String {
        if (url.startsWith("file://") || (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("content://") && !url.startsWith("asset://"))) {
            val file = if (url.startsWith("file://")) File(java.net.URI.create(url)) else File(url)
            return Text.decodePlaylist(file.readBytes())
        }
        if (url.startsWith("asset://")) {
            val stream =
                assetStreamResolver?.invoke(url.removePrefix("asset://"))
                    ?: throw IllegalArgumentException("Asset not found: $url")
            return Text.decodePlaylist(stream.use { it.readBytes() })
        }
        if (url.startsWith("content://")) {
            val stream =
                contentStreamResolver?.invoke(url)
                    ?: throw IllegalArgumentException("Unsupported URL schema: $url")
            val bytes = stream.use { it.readBytes() }
            return Text.decodePlaylist(bytes)
        }
        try {
            return Http.getText(url, ua)
        } catch (e: Exception) {
            val filename = url.substringAfterLast('/')
            if (filename.endsWith(".m3u", true) || filename.endsWith(".m3u8", true)) {
                assetStreamResolver?.invoke("playlists/$filename")?.use { stream ->
                    return Text.decodePlaylist(stream.readBytes())
                }
            }
            throw e
        }
    }

    /**
     * check.lua: быстрый скан HEAD (таймаут 4 с, 10 сессий), затем повторная проверка
     * «сомнительных» GET-запросом с таймаутом 6 с. Рабочими считаются ответы 200 и 302.
     */
    private suspend fun checkStreams(
        src: SourceConfig,
        list: List<SourceChannel>,
        progress: Progress,
    ): List<SourceChannel> =
        coroutineScope {
            val sem = Semaphore(16)
            val done = AtomicInteger()
            val good = AtomicInteger()

            suspend fun probe(
                ch: SourceChannel,
                method: String,
                timeout: Int,
            ): Boolean =
                sem.withPermit {
                    val ok =
                        runCatching {
                            Http.open(
                                ch.variant.url,
                                ch.variant.userAgent ?: UserAgents.DUMP_CHECK,
                                method = method,
                                connectTimeoutMs = timeout,
                                readTimeoutMs = timeout,
                                followRedirects = false,
                            ).use { it.code == 200 || it.code == 302 || it.code == 301 || it.code == 307 }
                        }.getOrDefault(false)
                    ok
                }
            val first =
                list.map { ch ->
                    async(Dispatchers.IO) {
                        val ok = probe(ch, "HEAD", 4000)
                        val n = done.incrementAndGet()
                        if (ok) good.incrementAndGet()
                        if (n % 25 == 0 || n == list.size) progress.report(Messages[Messages.Key.PLAYLIST_CHECKING, src.name, n, list.size, good.get()])
                        ch to ok
                    }
                }.awaitAll()
            val retry = first.filter { !it.second }.map { it.first }
            done.set(0)
            val second =
                retry.map { ch ->
                    async(Dispatchers.IO) {
                        val ok = probe(ch, "GET", 6000)
                        val n = done.incrementAndGet()
                        if (ok) good.incrementAndGet()
                        if (n % 25 == 0 || n == retry.size) progress.report(Messages[Messages.Key.PLAYLIST_RECHECKING, src.name, n, retry.size, good.get()])
                        ch to ok
                    }
                }.awaitAll()
            val okSet = (first + second).filter { it.second }.map { it.first.variant.url }.toHashSet()
            list.filter { it.variant.url in okSet }
        }

    // ------------------------------------------------------------------ хранение

    private fun save(
        id: String,
        list: List<SourceChannel>,
    ) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(dir, "$id.json.tmp")
        val dst = File(dir, "$id.json")
        tmp.writeText(arr.toString())
        // renameTo молча возвращает false (на Windows — если приёмник существует),
        // поэтому проверяем результат и пробуем delete + rename.
        if (!tmp.renameTo(dst)) {
            dst.delete()
            if (!tmp.renameTo(dst)) {
                tmp.delete()
                throw java.io.IOException("Failed to save playlist cache for $id")
            }
        }
    }

    private fun load(id: String): List<SourceChannel> {
        val f = File(dir, "$id.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun hasData(): Boolean =
        settings.sources.any {
            it.enabled && (if (isBundled(it)) bundled.containsKey(it) else File(dir, "${it.id}.json").exists())
        }

    fun deleteSource(id: String) {
        File(dir, "$id.json").delete()
    }

    /** Встроенные списки берём из текущей версии приложения, остальные — из кэша. */
    suspend fun loadMerged(): List<Channel> =
        withContext(Dispatchers.IO) {
            val byKey = LinkedHashMap<String, MutableList<SourceChannel>>()
            for (src in settings.sources.filter { it.enabled }) {
                val channels =
                    if (isBundled(src)) {
                        mergeBundled(loadBundled(src), load(onlineCacheId(src)))
                    } else {
                        load(src.id)
                    }
                for (ch in channels) {
                    // Keep existing favourites and EPG links when displaying the corrected HD label.
                    val nameKey = Text.channelKey(ch.name)
                    val key =
                        if (src.id in cultureHdSources && nameKey == "россия к hd") {
                            "россия к"
                        } else {
                            nameKey
                        }
                    byKey.getOrPut(key) { ArrayList() } += ch
                }
            }
            var n = 0
            byKey.map { (key, list) ->
                val first = list.first()
                Channel(
                    key = key,
                    name = first.name,
                    group =
                        list.firstOrNull { it.group != "Новые каналы" && it.group != "Билайн" }?.group
                            ?: list.firstOrNull { it.group != "Новые каналы" }?.group
                            ?: first.group,
                    logo = list.firstNotNullOfOrNull { it.logo },
                    tvgId = list.firstNotNullOfOrNull { it.tvgId },
                    tvgName = list.firstNotNullOfOrNull { it.tvgName },
                    variants = list.map { it.variant }.distinctBy { it.url },
                    number = ++n,
                )
            }
        }

    private fun toJson(c: SourceChannel) =
        JSONObject().apply {
            put("n", c.name)
            put("g", c.group)
            put("l", c.logo ?: "")
            put("id", c.tvgId ?: "")
            put("tn", c.tvgName ?: "")
            val v = c.variant
            put("u", v.url)
            put("s", v.sourceId)
            put("sn", v.sourceName)
            put("k", v.kind.name)
            put("ua", v.userAgent ?: "")
            put("ref", v.referer ?: "")
            put("ck", v.clearKey ?: "")
            if (v.headers.isNotEmpty()) put("h", JSONObject(v.headers as Map<*, *>))
            v.catchup?.let {
                put("ct", it.type)
                put("cd", it.days)
                put("cs", it.source ?: "")
            }
        }

    private fun fromJson(o: JSONObject): SourceChannel {
        val headers = o.optJSONObject("h")?.let { h -> h.keys().asSequence().associateWith { h.getString(it) } } ?: emptyMap()
        val catchup = if (o.has("ct")) Catchup(o.getString("ct"), o.getDouble("cd"), o.optString("cs").ifBlank { null }) else null
        return SourceChannel(
            name = o.getString("n"),
            group = o.getString("g"),
            logo = o.optString("l").ifBlank { null },
            tvgId = o.optString("id").ifBlank { null },
            tvgName = o.optString("tn").ifBlank { null },
            variant =
                StreamVariant(
                    url = o.getString("u"), sourceId = o.getString("s"), sourceName = o.getString("sn"),
                    kind = StreamKind.valueOf(o.optString("k", "GENERIC")),
                    userAgent = o.optString("ua").ifBlank { null },
                    referer = o.optString("ref").ifBlank { null },
                    headers = headers,
                    clearKey = o.optString("ck").ifBlank { null },
                    catchup = catchup,
                ),
        )
    }
}
