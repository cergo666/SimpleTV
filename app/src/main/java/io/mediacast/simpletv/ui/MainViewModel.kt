package io.mediacast.simpletv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.mediacast.simpletv.data.StreamChecker
import io.mediacast.simpletv.data.StreamResolver
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.ChannelArranger
import io.mediacast.simpletv.data.EpgRepository
import io.mediacast.simpletv.data.EpgSource
import io.mediacast.simpletv.data.PlaylistRepository
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.Quality
import io.mediacast.simpletv.data.Settings
import io.mediacast.simpletv.data.SourceConfig
import io.mediacast.simpletv.data.AndroidSettings
import io.mediacast.simpletv.data.AndroidPlaylistRepository
import io.mediacast.simpletv.player.PlayerController
import io.mediacast.simpletv.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class Overlay { NONE, HOME, PLAYLIST, EPG, SETTINGS, STREAM, GUIDE }

data class Group(val name: String, val count: Int, val special: Boolean = false)

data class Toast(val text: String, val id: Long = System.nanoTime())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
    private fun plurals(id: Int, count: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(id, count, *args)
    val settings = AndroidSettings(app)
    val playlists = AndroidPlaylistRepository(app, settings)
    val epg = EpgRepository(app, settings)
    val controller = PlayerController(app, settings)

    init {
        C.apply(settings.accent)
    }

    fun setAccent(id: String) {
        settings.accent = id
        C.apply(id)
    }

    private val _uiMode = MutableStateFlow(settings.uiMode)
    val uiMode: StateFlow<String> = _uiMode.asStateFlow()

    fun setUiMode(mode: String) {
        settings.uiMode = mode
        _uiMode.value = mode
    }

    private val _settingsRevision = MutableStateFlow(0)
    val settingsRevision: StateFlow<Int> = _settingsRevision.asStateFlow()

    fun notifySettingsChanged() {
        _settingsRevision.value++
    }

    private val _qualityRevision = MutableStateFlow(0)
    val qualityRevision: StateFlow<Int> = _qualityRevision.asStateFlow()

    fun selectQuality(q: Quality?) {
        controller.setQuality(q)
        _qualityRevision.value++
    }

    fun togglePlayPause() {
        val p = controller.player
        if (p.playWhenReady) p.pause() else p.play()
    }

    fun seekArchive(deltaMs: Long): Long? {
        if (controller.state.value.archive == null) return null
        return controller.seekBy(deltaMs)
    }

    private val _overlay = MutableStateFlow(Overlay.NONE)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    fun setOverlay(o: Overlay) { _overlay.value = o }
    fun closeOverlay() { _overlay.value = Overlay.NONE }

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _favorites = MutableStateFlow(settings.favorites)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _recent = MutableStateFlow(settings.recentChannels)
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private fun rememberChannel(ch: Channel) {
        if (_recent.value.firstOrNull() == ch.key) {
            // lastChannel всё равно обновляем, но диск не дёргаем без изменений списка.
            viewModelScope.launch(Dispatchers.IO) { settings.lastChannel = ch.key }
            return
        }
        val recent = (listOf(ch.key) + _recent.value.filterNot { it == ch.key }).take(12)
        _recent.value = recent
        // Файл настроек пишем в IO: save() синхронно ходит на диск, на Main это дёргает скролл.
        viewModelScope.launch(Dispatchers.IO) {
            settings.lastChannel = ch.key
            settings.recentChannels = recent
        }
    }

    fun previousChannel(): Channel? {
        val current = controller.state.value.channel?.key
        val byKey = _channels.value.associateBy { it.key }
        return _recent.value.asSequence().filter { it != current }.mapNotNull { byKey[it] }.firstOrNull()
    }

    fun playPreviousChannel() { previousChannel()?.let { play(it) } }

    /** Меняется после обновления EPG — чтобы экраны перечитали программу. */
    private val _epgVersion = MutableStateFlow(0)
    val epgVersion: StateFlow<Int> = _epgVersion.asStateFlow()

    private val _playlistBusy = MutableStateFlow<String?>(null)
    val playlistBusy: StateFlow<String?> = _playlistBusy.asStateFlow()
    private val _epgBusy = MutableStateFlow<String?>(null)
    val epgBusy: StateFlow<String?> = _epgBusy.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _showStats = MutableStateFlow(settings.showStats)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    fun toggleStats() {
        _showStats.value = !_showStats.value
        val v = _showStats.value
        viewModelScope.launch(Dispatchers.IO) { settings.showStats = v }
    }

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Список, внутри которого переключаемся кнопками вверх/вниз (текущая группа). */
    private var zapList: List<Channel> = emptyList()
    /** Объединённый список до фильтров (SD-дубли, часовые пояса, порядок). */
    private var raw: List<Channel> = emptyList()
    /** Обновления плейлиста и EPG сериализуем мьютексом, а не join/early-return:
     *  параллельный refresh ждёт своей очереди вместо тихого return,
     *  а clear не может перемешаться с rebuild. */
    private val plsMutex = Mutex()
    private val epgMutex = Mutex()

    private fun arrange(list: List<Channel>) = ChannelArranger.arrange(
        list, ChannelArranger.Options(
            settings.hideSdDuplicates,
            settings.hideTimezones,
            settings.standardOrder,
            settings.mergeOtt,
            settings.hideAdult,
            settings.preferMaxQuality,
        )
    )

    /** Пересчитать список после смены фильтров в настройках. */
    fun reapplyFilters() = applyChannels(raw)

    init {
        ImageLoader.cacheDir = File(app.cacheDir, "logos").apply { mkdirs() }
        // Каталог логотипов не должен отменять весь стартап: без него работают EPG-логотипы.
        runCatching { ImageLoader.loadCatalog(app.assets) }
        controller.onMessage = { show(it) }
        controller.onStreamSuccess = { ch, index ->
            val v = ch.variants.getOrNull(index)
            if (v != null) {
                viewModelScope.launch(Dispatchers.IO) { settings.setPreferredVariant(ch.key, v.url) }
            }
        }
        viewModelScope.launch {
            raw = playlists.loadMerged()
            val list = arrange(raw)
            _channels.value = list
            zapList = list
            _loaded.value = true
            val last = settings.lastChannel?.let { k -> list.find { it.key == k } } ?: list.firstOrNull()
            last?.let { play(it) }
            startStreamProber()
            autoUpdate()
        }
        // Как планировщик SimpleTV: периодически проверяем, не пора ли обновиться
        viewModelScope.launch {
            while (isActive) {
                delay(60 * 60 * 1000L)
                autoUpdate()
            }
        }
    }

    private var proberJob: Job? = null

    private fun startStreamProber() {
        proberJob?.cancel()
        proberJob = viewModelScope.launch(Dispatchers.IO) {
            val toProbe = raw
            if (toProbe.isEmpty()) return@launch
            StreamChecker.probeChannels(toProbe, settings.globalUserAgent)
            withContext(Dispatchers.Main) {
                reapplyFilters()
            }
        }
    }

    private fun autoUpdate() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        if (!playlists.hasData() || playlists.needsBundledUpdate() ||
            now - settings.lastPlaylistUpdate > settings.playlistIntervalDays * day
        ) refreshPlaylists()
        // EPG — после плейлиста: в базу попадает программа только каналов плейлиста
        if (playlists.hasData() && (epg.isEmpty || now - settings.lastEpgUpdate > settings.epgIntervalDays * day)) refreshEpg(force = false)
    }

    fun show(text: String) { _toast.value = Toast(text) }

    // ------------------------------------------------------------ обновления

    fun refreshPlaylists() {
        viewModelScope.launch {
            plsMutex.withLock {
                _playlistBusy.value = text(R.string.playlist_busy_refresh)
                val list = runCatching {
                    playlists.refreshAll({ msg -> _playlistBusy.value = msg }) { partial -> withContext(Dispatchers.Main) { applyChannels(partial) } }
                }.getOrElse { e -> show(text(R.string.error_refresh, e.message ?: "")); null }
                if (list != null) {
                    applyChannels(list)
                    startStreamProber()
                    show(plurals(R.plurals.playlists_updated, list.size, list.size))
                }
                _playlistBusy.value = null
                if (list != null) {
                    // EPG-операции идут под своим мьютексом: пользователь может
                    // нажать «обновить телепрограмму» одновременно с плейлистом.
                    epgMutex.withLock {
                        if (epg.isEmpty) refreshEpgLocked(force = false)
                        else if (withContext(Dispatchers.IO) { epg.needsRelink(list) }) relinkEpgLocked()
                    }
                }
            }
        }
    }

    /** Вызывать только под epgMutex. */
    private suspend fun refreshEpgLocked(force: Boolean) {
        _epgBusy.value = text(R.string.epg_checking)
        val changed = runCatching { epg.update(force, _channels.value) { msg -> _epgBusy.value = msg } }
            .getOrElse { e -> show(text(R.string.error_epg, e.message ?: "")); false }
        if (changed) {
            bumpEpgVersion()
            show(settings.epgStatus)
        } else if (force) show(settings.epgStatus)
        _epgBusy.value = null
    }

    fun refreshEpg(force: Boolean) {
        viewModelScope.launch {
            epgMutex.withLock { refreshEpgLocked(force) }
        }
    }

    private suspend fun relinkEpgLocked() {
        _epgBusy.value = text(R.string.epg_relink)
        if (runCatching { epg.relink(_channels.value) { _epgBusy.value = it } }.getOrDefault(false)) bumpEpgVersion()
        _epgBusy.value = null
    }

    fun clearEpg() {
        // Очистка идёт через тот же мьютекс, что и обновления, — иначе
        // clear() может перемешаться с rebuild из параллельного refreshEpg.
        viewModelScope.launch {
            epgMutex.withLock {
                withContext(Dispatchers.IO) { epg.clear() }
                bumpEpgVersion()
                _epgBusy.value = text(R.string.epg_checking)
                val changed = runCatching { epg.update(force = true, _channels.value) { msg -> _epgBusy.value = msg } }
                    .getOrElse { e -> show(text(R.string.error_epg, e.message ?: "")); false }
                if (changed) {
                    bumpEpgVersion()
                    show(text(R.string.epg_updated))
                } else {
                    show(settings.epgStatus)
                }
                _epgBusy.value = null
            }
        }
    }

    private fun applyChannels(source: List<Channel>) {
        raw = source
        val list = arrange(source)
        _channels.value = list
        zapList = list
        val cur = controller.state.value.channel
        if (cur == null) list.firstOrNull()?.let { play(it, list) }
        else list.find { it.key == cur.key }?.let { fresh ->
            // адреса могли смениться — переоткрываем, только если поток стоит с ошибкой
            if (controller.state.value.error != null) play(fresh, list)
        }
    }

    private fun reloadMerged() {
        viewModelScope.launch { plsMutex.withLock { applyChannels(playlists.loadMerged()) } }
    }

    // ------------------------------------------------------------ воспроизведение

    fun play(ch: Channel, context: List<Channel>? = null, archive: Programme? = null) {
        val index = if (archive == null) preferredIndex(ch) else ch.variants.indexOfFirst {
            StreamResolver.catchupUrl(it, archive.start, archive.stop) != null
        }
        if (index < 0) {
            show(text(R.string.archive_unavailable))
            return
        }
        if (context != null && context.isNotEmpty()) zapList = context
        rememberChannel(ch)
        controller.play(ch, index, archive)
    }

    private fun preferredIndex(ch: Channel): Int {
        // Запомненный рабочий вариант важнее эвристики: сначала он, потом Beeline, потом любой доступный.
        val preferred = settings.preferredVariant(ch.key)
        val remembered = ch.variants.indexOfFirst {
            it.url == preferred && !StreamChecker.isUnavailable(it.url)
        }
        if (remembered >= 0) return remembered
        val beeline = ch.variants.indexOfFirst { it.sourceId == "beeline" && !StreamChecker.isUnavailable(it.url) }
        if (beeline >= 0) return beeline
        return ch.variants.indexOfFirst { !StreamChecker.isUnavailable(it.url) }.coerceAtLeast(0)
    }

    /** Ручной выбор источника канала; выбор запоминается для этого канала. */
    fun selectVariant(ch: Channel, index: Int) {
        val v = ch.variants.getOrNull(index) ?: return
        val state = controller.state.value
        val archive = state.archive?.takeIf { state.channel?.key == ch.key }
        if (archive != null && StreamResolver.catchupUrl(v, archive.start, archive.stop) == null) {
            show(text(R.string.archive_unavailable_source, v.sourceName))
            return
        }
        rememberChannel(ch)
        viewModelScope.launch(Dispatchers.IO) { settings.setPreferredVariant(ch.key, v.url) }
        controller.play(ch, index, archive)
        show(text(R.string.source_selected, v.sourceName, index + 1, ch.variants.size))
    }

    /** Следующий источник текущего канала (кнопка на пульте / в панели). */
    fun nextVariant() {
        val s = controller.state.value
        val ch = s.channel ?: return
        if (ch.variants.size < 2) { show(text(R.string.single_source)); return }
        val archive = s.archive
        val next = (1 until ch.variants.size)
            .map { (s.variantIndex + it) % ch.variants.size }
            .firstOrNull { archive == null || StreamResolver.catchupUrl(
                ch.variants[it], archive.start, archive.stop,
            ) != null }
        if (next == null) show(text(R.string.no_archive_source))
        else selectVariant(ch, next)
    }

    fun playArchive(ch: Channel, p: Programme) {
        play(ch, archive = p)
    }

    fun backToLive() {
        controller.state.value.channel?.let { play(it) }
    }

    fun zap(delta: Int) {
        val list = zapList.ifEmpty { _channels.value }
        if (list.isEmpty()) return
        val cur = controller.state.value.channel
        val i = list.indexOfFirst { it.key == cur?.key }
        val next = list[((if (i < 0) 0 else i + delta) % list.size + list.size) % list.size]
        play(next)
    }

    fun zapForward() = zap(if (settings.reverseZap) -1 else 1)
    fun zapBackward() = zap(if (settings.reverseZap) 1 else -1)

    fun playNumber(n: Int): Boolean {
        val ch = _channels.value.find { it.number == n } ?: return false
        play(ch, _channels.value)
        return true
    }

    fun toggleFavorite(ch: Channel) {
        val f = _favorites.value.toMutableSet()
        if (!f.add(ch.key)) f.remove(ch.key)
        _favorites.value = f
        viewModelScope.launch(Dispatchers.IO) { settings.favorites = f }
        show(if (ch.key in f) text(R.string.favorite_added, ch.name) else text(R.string.favorite_removed, ch.name))
    }

    fun groups(list: List<Channel>, fav: Set<String>): List<Group> {
        val out = ArrayList<Group>()
        val favCount = list.count { it.key in fav }
        if (favCount > 0) out += Group(FAV, favCount, true)
        out += Group(ALL, list.size, true)
        if (settings.hdGroups) {
            val uhd = list.count { ChannelArranger.isUhd(it.name) }
            val hd = list.count { ChannelArranger.isHd(it.name) && !ChannelArranger.isUhd(it.name) }
            if (hd > 0) out += Group(HD, hd, true)
            if (uhd > 0) out += Group(UHD, uhd, true)
        }
        val counts = LinkedHashMap<String, Int>()
        list.forEach { counts[it.group] = (counts[it.group] ?: 0) + 1 }
        counts.forEach { (g, c) -> out += Group(g, c) }
        return out
    }

    fun channelsOf(group: String, list: List<Channel>, fav: Set<String>): List<Channel> = when (group) {
        ALL -> list
        FAV -> list.filter { it.key in fav }
        HD -> list.filter { ChannelArranger.isHd(it.name) && !ChannelArranger.isUhd(it.name) }
        UHD -> list.filter { ChannelArranger.isUhd(it.name) }
        else -> list.filter { it.group == group }
    }

    // ------------------------------------------------------------ EPG (вызывается из UI в IO)

    private val nowNextCache = ConcurrentHashMap<String, List<Programme>>()
    private val scheduleCache = object : LinkedHashMap<String, List<Programme>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Programme>>?): Boolean = size > 256
    }

    private fun bumpEpgVersion() {
        nowNextCache.clear()
        synchronized(scheduleCache) { scheduleCache.clear() }
        _epgVersion.value++
    }

    fun peekNowNext(ch: Channel, now: Long): List<Programme>? =
        nowNextCache["${ch.key}|${now / 60_000}"]

    suspend fun nowNextCached(ch: Channel, now: Long): List<Programme> {
        val key = "${ch.key}|${now / 60_000}"
        nowNextCache[key]?.let { return it }
        val list = withContext(Dispatchers.IO) { epg.nowNext(ch, now) }
        if (nowNextCache.size > 600) nowNextCache.clear()
        nowNextCache[key] = list
        return list
    }

    fun peekSchedule(ch: Channel, from: Long, to: Long): List<Programme>? =
        synchronized(scheduleCache) { scheduleCache["${ch.key}|$from|$to"] }

    suspend fun scheduleCached(ch: Channel, from: Long, to: Long): List<Programme> {
        val key = "${ch.key}|$from|$to"
        synchronized(scheduleCache) { scheduleCache[key] }?.let { return it }
        val list = withContext(Dispatchers.IO) { epg.schedule(ch, from, to) }
        synchronized(scheduleCache) { scheduleCache[key] = list }
        return list
    }

    suspend fun nowNext(ch: Channel): List<Programme> = withContext(Dispatchers.IO) { epg.nowNext(ch) }
    suspend fun schedule(ch: Channel, from: Long, to: Long): List<Programme> = withContext(Dispatchers.IO) { epg.schedule(ch, from, to) }
    suspend fun guideMatches(channels: List<Channel>, query: String, now: Long): Map<String, Programme> =
        withContext(Dispatchers.IO) { epg.guideMatches(channels, query, now) }
    fun logoOf(ch: Channel): String? = ch.logo ?: epg.iconFor(ch)

    // ------------------------------------------------------------ настройки

    fun updateSource(src: SourceConfig) {
        settings.sources = settings.sources.map { if (it.id == src.id) src else it }
        reloadMerged()
    }

    fun addSource(name: String, url: String, ua: String?) {
        val normalized = url.trim()
        // Повторное открытие того же .m3u не должно плодить дубликаты — обновляем существующий источник
        settings.sources.firstOrNull { it.urls.firstOrNull() == normalized }?.let {
            refreshSource(it)
            return
        }
        val src = SourceConfig(id = "user" + System.currentTimeMillis(), name = name.ifBlank { text(R.string.default_playlist_name) }, urls = listOf(normalized), streamUserAgent = ua?.ifBlank { null }, playlistUserAgent = ua?.ifBlank { null }, sort = 20 + settings.sources.size)
        settings.sources = settings.sources + src
        refreshSource(src)
    }

    private fun refreshSource(src: SourceConfig) {
        viewModelScope.launch {
            // Загрузки источников идут под общим мьютексом плейлиста, чтобы
            // refreshPlaylists не пропускал обновление молча.
            plsMutex.withLock {
                _playlistBusy.value = text(R.string.source_loading, src.name)
                runCatching { playlists.refreshOne(src) { _playlistBusy.value = it } }
                    .onFailure { show(text(R.string.source_error, src.name, it.message ?: "")) }
                applyChannels(playlists.loadMerged())
                _playlistBusy.value = null
            }
        }
    }

    fun removeSource(src: SourceConfig) {
        if (src.builtIn) return
        settings.sources = settings.sources.filter { it.id != src.id }
        playlists.deleteSource(src.id)
        reloadMerged()
    }

    fun updateEpgSource(src: EpgSource) {
        settings.epgSources = settings.epgSources.map { if (it.id == src.id) src else it }
        refreshEpg(force = false)
    }

    fun addEpgSource(name: String, url: String) {
        settings.epgSources = settings.epgSources + EpgSource("u" + System.currentTimeMillis(), name.ifBlank { url }, url.trim(), true)
        refreshEpg(force = false)
    }

    fun removeEpgSource(src: EpgSource) {
        settings.epgSources = settings.epgSources.filter { it.id != src.id }
        refreshEpg(force = false)
    }

    override fun onCleared() {
        controller.release()
    }

    companion object {
        // Нейтральные ключи специальных групп: отображаемое название берётся
        // из ресурсов (groupLabel), значение не зависит от языка интерфейса
        const val ALL = "key:all"
        const val FAV = "key:favourites"
        const val NOW = "key:now"
        const val RECENT = "key:recent"
        const val HD = "key:hd"
        const val UHD = "key:uhd"
    }
}
