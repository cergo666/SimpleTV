package io.mediacast.simpletv.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import io.mediacast.simpletv.data.AndroidSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w1280dp-h720dp")
class TvNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val settings = AndroidSettings(context)
                    settings.sources = settings.sources.map { it.copy(enabled = false) }
                    settings.epgSources = emptyList()
                    settings.uiMode = "tv"
                    base.evaluate()
                }
            }
        },
    ).around(compose)

    private fun viewModel(): MainViewModel {
        var vm: MainViewModel? = null
        compose.activityRule.scenario.onActivity { activity ->
            vm = ViewModelProvider(activity)[MainViewModel::class.java]
        }
        return requireNotNull(vm)
    }

    @Test
    fun tvPlaylistDisplaysLocalizedAllChannelsHeader() {
        val vm = viewModel()
        compose.runOnUiThread {
            vm.setUiMode("tv")
            vm.setOverlay(Overlay.PLAYLIST)
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("All channels").assertCountEquals(2)
        compose.onAllNodesWithText("All channels")[0].assertIsDisplayed()
        compose.onAllNodesWithText("All channels")[1].assertIsDisplayed()
        compose.onNodeWithText("key:all").assertDoesNotExist()
    }

    @Test
    fun browseHomeDisplaysTopNavigationActions() {
        val vm = viewModel()
        compose.runOnUiThread {
            vm.setUiMode("tv")
            vm.setOverlay(Overlay.HOME)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Channels").assertIsDisplayed()
        compose.onNodeWithText("Programme guide").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsToggleUpdatesInPlaceAndSwitchesUiMode() {
        val vm = viewModel()
        compose.runOnUiThread {
            vm.setUiMode("auto")
            vm.setOverlay(Overlay.SETTINGS)
        }
        compose.waitForIdle()

        compose.onNodeWithText("Channels").performClick()
        compose.waitForIdle()
        assertTrue(vm.settings.hideAdult)
        compose.onNodeWithText("Hide adult channels (18+)").performClick()
        compose.waitForIdle()
        assertFalse(vm.settings.hideAdult)
        compose.onNodeWithText("Hide adult channels (18+)").assertIsDisplayed()

        compose.onNodeWithText("Appearance").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Interface mode").assertIsDisplayed()
        compose.onNodeWithText("Auto").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Android TV").assertIsDisplayed()
    }

    @Test
    fun tvGuideRendersHeaderAndControls() {
        val vm = viewModel()
        compose.runOnUiThread {
            vm.setUiMode("tv")
            vm.setOverlay(Overlay.EPG)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Programme guide").assertIsDisplayed()
        compose.onNodeWithText("Now").assertIsDisplayed()
    }
}
