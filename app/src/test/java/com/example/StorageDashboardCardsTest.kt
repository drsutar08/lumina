package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.DashboardStorageSection
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StorageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StorageDashboardCardsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storageCardsDisplayUsedVsTotalProgressBarAndCategoryBreakdowns() {
        var manageClicks = 0
        var quickSyncClicks = 0

        val stats = StorageStats(
            usedBytesCloud = 3L * 1024 * 1024 * 1024,
            totalLimitCloud = 15L * 1024 * 1024 * 1024,
            localUnsyncedBytes = 25L * 1024 * 1024,
            localUnsyncedCount = 4,
            syncedCount = 10,
            totalPhotoCount = 14,
            totalLibraryBytes = 75L * 1024 * 1024,
            photosBytes = 40L * 1024 * 1024,
            photosCount = 9,
            videosBytes = 30L * 1024 * 1024,
            videosCount = 3,
            gifsAndDocsBytes = 5L * 1024 * 1024,
            gifsAndDocsCount = 2,
            syncedPhotosBytes = 30L * 1024 * 1024,
            syncedVideosBytes = 20L * 1024 * 1024,
            deviceTotalBytes = 64L * 1024 * 1024 * 1024,
            deviceAvailableBytes = 20L * 1024 * 1024 * 1024,
            percentUsed = 0.2f
        )

        composeRule.setContent {
            MyApplicationTheme {
                DashboardStorageSection(
                    storageStats = stats,
                    onManageStorageClick = { manageClicks++ },
                    onQuickSyncClick = { quickSyncClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_storage_section").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_cloud_storage_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_device_storage_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_library_storage_card").assertIsDisplayed()

        // Visual progress bars for used vs. total space
        composeRule.onNodeWithTag("dashboard_cloud_storage_progress_bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_device_storage_progress_bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_library_storage_progress_bar", useUnmergedTree = true).assertIsDisplayed()

        // Used vs. total texts
        composeRule.onNodeWithText("3.0 GB / 15.0 GB", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("44.0 GB / 64.0 GB", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("75.0 MB", substring = true).assertIsDisplayed()

        // Categorized breakdown labels
        assertTrue(composeRule.onAllNodesWithText("Photos", substring = true).fetchSemanticsNodes().size >= 2)
        assertTrue(composeRule.onAllNodesWithText("Videos", substring = true).fetchSemanticsNodes().size >= 2)
        assertTrue(composeRule.onAllNodesWithText("Documents", substring = true).fetchSemanticsNodes().size >= 2)

        composeRule.onNodeWithTag("dashboard_cloud_storage_card").performClick()
        assertEquals(1, manageClicks)

        composeRule.onNodeWithTag("dashboard_library_quick_sync_button").performClick()
        assertEquals(1, quickSyncClicks)
    }
}
