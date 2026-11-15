package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.components.SlideshowPlayerBar
import com.example.ui.components.SlideshowSettingsDialog
import com.example.ui.components.slideshowTransitionSpec
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SlideshowTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlideshowPlaybackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playerBarUpdatesPlayStateAndCyclesSlides() {
        var isPlaying by mutableStateOf(false)
        var currentIndex by mutableIntStateOf(0)
        val total = 4
        var settingsOpened = false

        composeRule.setContent {
            MyApplicationTheme {
                SlideshowPlayerBar(
                    isPlaying = isPlaying,
                    currentIndex = currentIndex,
                    totalCount = total,
                    intervalSeconds = 4,
                    transition = SlideshowTransition.CROSSFADE,
                    progress = if (isPlaying) 0.5f else 0f,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onPrevious = { currentIndex = if (currentIndex <= 0) total - 1 else currentIndex - 1 },
                    onNext = { currentIndex = (currentIndex + 1) % total },
                    onOpenSettings = { settingsOpened = true },
                    testTagPrefix = "hero_slideshow"
                )
            }
        }

        composeRule.onNodeWithTag("hero_slideshow_player_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_slideshow_counter_text").assertTextContains("Slide 1 of 4")
        composeRule.onNodeWithTag("hero_slideshow_config_summary").assertTextContains("4s • Crossfade")

        composeRule.onNodeWithTag("hero_slideshow_play_pause_button").performClick()
        assertTrue(isPlaying)
        composeRule.onNodeWithTag("hero_slideshow_counter_text").assertTextContains("Slideshow • 1 / 4")

        composeRule.onNodeWithTag("hero_slideshow_next_button").performClick()
        assertEquals(1, currentIndex)
        composeRule.onNodeWithTag("hero_slideshow_counter_text").assertTextContains("Slideshow • 2 / 4")

        composeRule.onNodeWithTag("hero_slideshow_prev_button").performClick()
        assertEquals(0, currentIndex)

        composeRule.onNodeWithTag("hero_slideshow_settings_button").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun settingsDialogConfiguresIntervalTransitionAndLoop() {
        var interval by mutableIntStateOf(4)
        var transition by mutableStateOf(SlideshowTransition.CROSSFADE)
        var loopEnabled by mutableStateOf(true)
        var dismissed = false

        composeRule.setContent {
            MyApplicationTheme {
                SlideshowSettingsDialog(
                    intervalSeconds = interval,
                    transition = transition,
                    loopEnabled = loopEnabled,
                    onIntervalSelected = { interval = it },
                    onTransitionSelected = { transition = it },
                    onLoopToggled = { loopEnabled = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("slideshow_interval_value").assertTextContains("4s per photo")
        composeRule.onNodeWithTag("interval_preset_6s").performScrollTo().performClick()
        assertEquals(6, interval)
        composeRule.onNodeWithTag("slideshow_interval_value").assertTextContains("6s per photo")

        composeRule.onNodeWithTag("transition_option_zoom").performScrollTo().performClick()
        assertEquals(SlideshowTransition.ZOOM, transition)

        composeRule.onNodeWithTag("slideshow_loop_switch").performScrollTo().performClick()
        assertFalse(loopEnabled)

        composeRule.onNodeWithTag("slideshow_settings_done_button").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun allTransitionStylesProduceValidSpecs() {
        SlideshowTransition.entries.forEach { style ->
            assertNotNull(slideshowTransitionSpec(style, 1))
            assertNotNull(slideshowTransitionSpec(style, -1))
        }
    }
}
