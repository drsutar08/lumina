package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.CropAspectRatio
import com.example.data.local.ImageEditProcessor
import com.example.data.local.NormalizedCropRect
import com.example.ui.components.PhotoEditorContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rotateAndCropBitmapProducesExpectedDimensions() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)

        val rotated90 = ImageEditProcessor.rotateBitmap(source, 90)
        assertEquals(100, rotated90.width)
        assertEquals(200, rotated90.height)

        val cropped = ImageEditProcessor.cropBitmap(
            source,
            NormalizedCropRect(left = 0.25f, top = 0.2f, right = 0.75f, bottom = 0.8f)
        )
        assertEquals(100, cropped.width)
        assertEquals(60, cropped.height)

        val squareCrop = ImageEditProcessor.computeCenteredCropRect(
            imageWidth = 200,
            imageHeight = 100,
            aspectRatio = CropAspectRatio.SQUARE
        )
        val combined = ImageEditProcessor.applyCropAndRotate(
            source = source,
            rotationDegrees = 90,
            cropRect = ImageEditProcessor.computeCenteredCropRect(100, 200, CropAspectRatio.SQUARE)
        )
        assertEquals(100, combined.width)
        assertEquals(100, combined.height)
        assertEquals(0.25f, squareCrop.left, 0.01f)
        assertEquals(0.75f, squareCrop.right, 0.01f)
    }

    @Test
    fun editorUiHandlesRotationAspectPresetResetAndSave() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        var capturedRotation = -1
        var capturedCrop: NormalizedCropRect? = null
        var capturedSaveAsCopy: Boolean? = null

        composeRule.setContent {
            MyApplicationTheme {
                PhotoEditorContent(
                    sourceBitmap = source,
                    isSaving = false,
                    onSave = { rotation, crop, saveAsCopy ->
                        capturedRotation = rotation
                        capturedCrop = crop
                        capturedSaveAsCopy = saveAsCopy
                    },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("photo_editor_root").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_dimensions_label").assertTextContains("200×100 px • 0°")

        composeRule.onNodeWithTag("editor_rotate_right_button").performClick()
        composeRule.onNodeWithTag("editor_rotation_angle").assertTextContains("90°")
        composeRule.onNodeWithTag("editor_dimensions_label").assertTextContains("100×200 px • 90°")

        composeRule.onNodeWithTag("editor_aspect_square").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_dimensions_label").assertTextContains("100×100 px • 90°")

        composeRule.onNodeWithTag("editor_reset_button").performClick()
        composeRule.onNodeWithTag("editor_dimensions_label").assertTextContains("200×100 px • 0°")

        composeRule.onNodeWithTag("editor_rotate_left_button").performClick()
        composeRule.onNodeWithTag("editor_rotation_angle").assertTextContains("270°")

        composeRule.onNodeWithTag("editor_save_copy_button").performClick()
        assertEquals(270, capturedRotation)
        assertTrue(capturedSaveAsCopy == true)
        assertTrue(capturedCrop?.isFullFrame() == true)

        composeRule.onNodeWithTag("editor_save_button").performClick()
        assertFalse(capturedSaveAsCopy == true)
    }
}
