package io.mediacast.simpletv.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import io.mediacast.simpletv.data.AndroidSettings
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-port")
class PlayerSearchTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val settings = AndroidSettings(context)
                settings.sources = settings.sources.map { it.copy(enabled = false) }
                settings.epgSources = emptyList()
                base.evaluate()
            }
        }
    }).around(compose)

    @Test
    fun startsPlayerAndAcceptsSearchInEnglish() {
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[MainViewModel::class.java].setOverlay(Overlay.PLAYLIST)
        }
        compose.onNodeWithText("Search channels").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("News")
        compose.onNodeWithText("News").assertIsDisplayed()
    }
}
