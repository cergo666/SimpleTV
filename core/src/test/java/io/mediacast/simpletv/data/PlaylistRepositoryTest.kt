package io.mediacast.simpletv.data

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaylistRepositoryTest {
    @Test
    fun onlineSnapshotAddsChannelsAndArchiveWithoutReplacingBundledMetadata() =
        runBlocking {
            val dir = Files.createTempDirectory("simpletv-playlist-merge-").toFile()
            try {
                val settings = Settings(JsonSettingsStore(File(dir, "settings.json")))
                settings.sources = Defaults.sources.map { it.copy(enabled = it.id == "beeline") }
                val url = "https://example.invalid/channel495.isml/manifest.mpd"
                val bundled =
                    "#EXTM3U\n#EXTINF:-1 tvg-id=\"fixture\" tvg-logo=\"https://example.invalid/logo.png\",Россия К HD\n$url\n" +
                        "#EXTINF:-1,Bundled Only\nhttps://example.invalid/local.mpd\n"
                val assetResolver: (String) -> java.io.InputStream? = { path ->
                    bundled.byteInputStream().takeIf { path == "playlists/beeline.m3u" }
                }
                val repo = PlaylistRepository(dir, settings, assetStreamResolver = assetResolver)
                val update =
                    File(dir, "online.m3u").apply {
                        writeText(
                            "#EXTM3U\n#EXTINF:-1 catchup-days=\"2\",Россия К\n$url\n" +
                                "#EXTINF:-1,Online Only\nhttps://example.invalid/online.mpd\n",
                        )
                    }
                val source = settings.sources.first()
                val fixture = source.copy(urls = listOf(source.urls.first(), update.absolutePath))
                assertTrue(repo.needsBundledUpdate())
                assertEquals(2, repo.loadMerged().size)
                repo.refreshOne(fixture) {}
                val merged = repo.loadMerged()
                assertEquals(3, merged.size)
                val culture = merged.single { it.key == "россия к" }
                assertEquals("Россия К HD", culture.name)
                assertEquals("fixture", culture.tvgId)
                assertEquals("https://example.invalid/logo.png", culture.logo)
                assertEquals(2.0, culture.variants.single().catchup?.days)

                update.writeText("<html>unavailable</html>")
                assertFailsWith<IllegalStateException> { repo.refreshOne(fixture) {} }
                val reopened = PlaylistRepository(dir, settings, assetStreamResolver = assetResolver)
                assertEquals(merged, reopened.loadMerged())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun bundledUpdateReplacesThePreviousCultureVariantAndKeepsItsKey() =
        runBlocking {
            val dir = Files.createTempDirectory("simpletv-playlist-test-").toFile()
            try {
                val settings = Settings(JsonSettingsStore(File(dir, "settings.json")))
                val custom = SourceConfig("custom", "Custom", listOf("https://example.invalid/list.m3u"))
                val retired = SourceConfig("tricolor", "Триколор", emptyList(), builtIn = true)
                settings.sources = Defaults.sources.map { if (it.id == "smotrim") it.copy(enabled = true) else it } + retired + custom
                assertEquals(Defaults.sources.map { it.id } + "custom", settings.sources.map { it.id })

                val repo =
                    PlaylistRepository(dir, settings, assetStreamResolver = { path ->
                        when (path) {
                            "playlists/wink.m3u" -> "#EXTM3U\n#EXTINF:-1 group-title=\"Федеральные\",Первый канал\nhttps://example.invalid/one.m3u8\n"
                            "playlists/beeline.m3u" -> "#EXTM3U\n#EXTINF:-1,Россия К HD\nhttps://example.invalid/channel495.isml/manifest.mpd\n#EXTINF:-1 group-title=\"Билайн\",Первый канал\nhttps://example.invalid/one.mpd\n"
                            "playlists/smotrim.m3u" -> "#EXTM3U\n#EXTINF:-1,Россия К HD\nhttps://example.invalid/kultura-hd/index.m3u8\n"
                            else -> null
                        }?.byteInputStream()
                    })
                File(dir, "sources/beeline.json").writeText(
                    """[{"n":"Россия К","g":"","u":"https://old.invalid/kultura","s":"beeline","sn":"Beeline"}]""",
                )

                val channels = repo.loadMerged()
                val culture = channels.single { it.key == "россия к" }
                assertEquals("Россия К HD", culture.name)
                assertEquals(listOf("beeline", "smotrim"), culture.variants.map { it.sourceId })
                assertEquals("https://example.invalid/channel495.isml/manifest.mpd", culture.variants.first().url)
                val first = channels.single { it.key == "первый канал" }
                assertEquals("Федеральные", first.group)
                assertEquals("beeline", first.variants.first().sourceId)
            } finally {
                dir.deleteRecursively()
            }
        }
}
