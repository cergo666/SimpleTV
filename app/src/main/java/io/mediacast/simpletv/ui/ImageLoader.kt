package io.mediacast.simpletv.ui

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import io.mediacast.simpletv.data.Http
import io.mediacast.simpletv.data.Text
import io.mediacast.simpletv.data.UserAgents
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Небольшой загрузчик логотипов: память + диск, уменьшение до ~160px. */
object ImageLoader {
    /** Память считаем в килобайтах (~16 МБ), а не в штуках: 400 несжатых битмапов — это ~40 МБ. */
    private val memory = object : LruCache<String, ImageBitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val failed = HashMap<String, Long>()
    private val net = Semaphore(6)
    private var catalog: Map<String, String> = emptyMap()
    var cacheDir: File? = null

    /** Неудачная загрузка скрывается лишь ненадолго — как в StreamChecker, чтобы логотип мог появиться позже. */
    private const val FAILURE_TTL_MS = 5 * 60 * 1000L

    /** Overridable in tests to avoid sleeping. */
    internal var clock: () -> Long = System::currentTimeMillis

    fun loadCatalog(assets: AssetManager) {
        if (catalog.isNotEmpty()) return
        val json = assets.open("logos.json").bufferedReader().use { JSONObject(it.readText()) }
        catalog = json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun fallback(name: String): String? = catalog[Text.channelKey(name)] ?: catalog[Text.looseKey(name)]

    fun cached(url: String): ImageBitmap? = memory.get(url)

    private fun isFailedRecently(url: String): Boolean = synchronized(failed) {
        // Карта растёт от уникальных битых URL — чистим протухшие при переполнении.
        // removeIf требует API 24, проект держит minSdk 23 — удаляем через итератор.
        if (failed.size > 2000) {
            val now = clock()
            val it = failed.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value + FAILURE_TTL_MS <= now) it.remove()
            }
        }
        val at = failed[url] ?: return false
        if (at + FAILURE_TTL_MS <= clock()) {
            failed.remove(url)
            return false
        }
        return true
    }

    /** Диск без лимита растёт вечно — держим не больше ~1000 свежих файлов. */
    private fun pruneDisk(dir: File, maxFiles: Int = 1000) {
        runCatching {
            val files = dir.listFiles() ?: return
            if (files.size <= maxFiles) return
            files.sortBy { it.lastModified() }
            for (i in 0 until files.size - maxFiles) files[i].delete()
        }
    }

    suspend fun load(url: String): ImageBitmap? {
        memory.get(url)?.let { return it }
        if (isFailedRecently(url)) return null
        return withContext(Dispatchers.IO) {
            val dir = cacheDir
            val file = dir?.let { File(it, md5(url)) }
            // Битый кэш не должен ронять LaunchedEffect — уходим в сеть.
            val bytes = file?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() } ?: net.withPermit {
                runCatching { Http.getBytes(url, UserAgents.TVS_DOWNLOAD) }.getOrNull()
            }?.also { b ->
                runCatching {
                    file?.writeBytes(b)
                    if (dir != null) pruneDisk(dir)
                }
            }
            val bmp = bytes?.let { decode(it) }
            if (bmp == null) synchronized(failed) { failed[url] = clock() } else memory.put(url, bmp)
            bmp
        }
    }

    private fun decode(b: ByteArray): ImageBitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 160 && o.outHeight / (sample * 2) >= 90) sample *= 2
        BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
