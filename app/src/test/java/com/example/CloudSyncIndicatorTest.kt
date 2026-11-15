package com.example

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.CloudSyncIndicatorButton
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CloudSyncIndicatorState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudSyncIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun indicatorDisplaysAllThreeStatesAndTriggersClick() {
        var clicks = 0
        val stateHolder = androidx.compose.runtime.mutableStateOf(CloudSyncIndicatorState.SYNCING)

        composeRule.setContent {
            MyApplicationTheme {
                CloudSyncIndicatorButton(
                    syncState = stateHolder.value,
                    onClick = { clicks++ },
                    showLabel = true
                )
            }
        }

        composeRule.onNodeWithTag("cloud_sync_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Syncing").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud_sync_indicator").performClick()
        assertEquals(1, clicks)

        stateHolder.value = CloudSyncIndicatorState.SYNCED
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Synced").assertIsDisplayed()

        stateHolder.value = CloudSyncIndicatorState.OFFLINE
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
    }
}
