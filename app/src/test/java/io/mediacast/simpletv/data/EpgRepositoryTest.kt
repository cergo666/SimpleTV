package io.mediacast.simpletv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgRepositoryTest {
    @Test
    fun malformedDownloadKeepsScheduleAndValidator() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AndroidSettings(context)
        val body = AtomicReference(xmltv("Working"))
        val etag = AtomicReference("working")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/guide") { request ->
            val bytes = body.get().toByteArray()
            request.responseHeaders.add("ETag", etag.get())
            request.sendResponseHeaders(200, bytes.size.toLong())
            request.responseBody.use { it.write(bytes) }
        }
        server.start()
        val source = EpgSource("fixture", "Fixture", "http://127.0.0.1:${server.address.port}/guide", true)
        settings.epgSources = listOf(source)
        val repository = EpgRepository(context, settings)
        try {
            val imported = repository.update(false, listOf(channel)) {}
            assertTrue(settings.epgStatus, imported)
            val cached = File(context.filesDir, "epg_cache/fixture.bin")
            assertEquals(body.get(), cached.readText())
            body.set(xmltv("Partial").removeSuffix("</tv>"))
            etag.set("broken")
            val replaced = repository.update(false, listOf(channel)) {}
            assertFalse(settings.epgStatus, replaced)
            assertEquals("Working", repository.nowNext(channel).single().title)
            assertEquals("working", settings.epgEtag(source.id))
            assertTrue(cached.readText().contains("Working"))
        } finally {
            server.stop(0)
            repository.clear()
        }
    }

    @Test
    fun disablingLastSourceClearsScheduleAndKeepsReusableCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AndroidSettings(context)
        val source = EpgSource("fixture", "Fixture", "https://example.invalid", true)
        settings.epgSources = listOf(source)
        val repository = EpgRepository(context, settings)
        try {
            val file = File(context.filesDir, "epg_cache/fixture.bin")
            file.writeText(xmltv("Working"))
            assertTrue(repository.relink(listOf(channel)) {})
            assertEquals("Working", repository.nowNext(channel).single().title)
            settings.epgSources = listOf(source.copy(enabled = false))
            assertTrue(repository.update(false, listOf(channel)) {})
            assertTrue(repository.nowNext(channel).isEmpty())
            assertTrue(file.exists())
            assertFalse(repository.update(false, listOf(channel)) {})
        } finally {
            repository.clear()
        }
    }

    private val channel = Channel(
        key = "one", name = "One", group = "", tvgId = "one",
        logo = null, tvgName = null, variants = emptyList(),
    )

    private fun xmltv(title: String): String {
        val now = System.currentTimeMillis()
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val start = format.format(Date(now - 120_000))
        val stop = format.format(Date(now + 120_000))
        return """
            <tv><channel id="one"><display-name>One</display-name></channel>
            <programme channel="one" start="$start" stop="$stop"><title>$title</title></programme></tv>
        """.trimIndent()
    }
}
