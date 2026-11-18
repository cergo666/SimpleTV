package io.mediacast.simpletv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First unit tests for the Android module, using Robolectric for SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun valuesSurviveANewSettingsInstance() {
        val settings = AndroidSettings(context)
        settings.globalUserAgent = "SimpleTV-test/1.0"
        settings.favorites = setOf("one", "two")
        settings.bufferMs = 8000

        val reopened = AndroidSettings(context)

        assertEquals("SimpleTV-test/1.0", reopened.globalUserAgent)
        assertEquals(setOf("one", "two"), reopened.favorites)
        assertEquals(8000, reopened.bufferMs)
    }

    @Test
    fun accentSurvivesANewSettingsInstance() {
        val settings = AndroidSettings(context)
        assertEquals("violet", settings.accent)
        settings.accent = "teal"
        assertEquals("teal", AndroidSettings(context).accent)
    }

    @Test
    fun sourceListRoundTripsThroughJson() {
        val settings = AndroidSettings(context)
        settings.sources = listOf(
            SourceConfig(id = "custom", name = "Custom", urls = listOf("https://example.test/list.m3u"), sort = 7),
        )

        val reopened = AndroidSettings(context)

        val saved = reopened.sources.single { it.id == "custom" }
        assertEquals(listOf("https://example.test/list.m3u"), saved.urls)
        assertEquals(7, saved.sort)
    }
}
