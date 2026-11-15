package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceIn(0f, 1f)
    val height: Float get() = (bottom - top).coerceIn(0f, 1f)

    fun isFullFrame(): Boolean =
        left <= 0.002f && top <= 0.002f && right >= 0.998f && bottom >= 0.998f

    fun normalizedClamped(minSize: Float = 0.12f): NormalizedCropRect {
        val safeMin = minSize.coerceIn(0.05f, 0.5f)
        var l = left.coerceIn(0f, 1f - safeMin)
        var t = top.coerceIn(0f, 1f - safeMin)
        var r = right.coerceIn(l + safeMin, 1f)
        var b = bottom.coerceIn(t + safeMin, 1f)
        if (r - l < safeMin) {
            l = (r - safeMin).coerceAtLeast(0f)
            r = (l + safeMin).coerceAtMost(1f)
        }
        if (b - t < safeMin) {
            t = (b - safeMin).coerceAtLeast(0f)
            b = (t + safeMin).coerceAtMost(1f)
        }
        return NormalizedCropRect(l, t, r, b)
    }
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    THREE_FOUR("3:4", 3f / 4f)
}

data class SavedEditedImage(
    val uriString: String,
    val byteSize: Long
)

object ImageEditProcessor {
    private const val TAG = "ImageEditProcessor"

    fun computeCenteredCropRect(
        imageWidth: Int,
        imageHeight: Int,
        aspectRatio: CropAspectRatio
    ): NormalizedCropRect {
        val targetRatio = aspectRatio.ratio ?: return NormalizedCropRect()
        if (imageWidth <= 0 || imageHeight <= 0) return NormalizedCropRect()

        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        return if (imageAspect > targetRatio) {
            val normWidth = (targetRatio / imageAspect).coerceIn(0.15f, 1f)
            val horizontalMargin = (1f - normWidth) / 2f
            NormalizedCropRect(
                left = horizontalMargin,
                top = 0f,
                right = 1f - horizontalMargin,
                bottom = 1f
            )
        } else {
            val normHeight = (imageAspect / targetRatio).coerceIn(0.15f, 1f)
            val verticalMargin = (1f - normHeight) / 2f
            NormalizedCropRect(
                left = 0f,
                top = verticalMargin,
                right = 1f,
                bottom = 1f - verticalMargin
            )
        }
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmap(source: Bitmap, cropRect: NormalizedCropRect): Bitmap {
        val clamped = cropRect.normalizedClamped()
        if (clamped.isFullFrame()) return source

        val srcWidth = source.width
        val srcHeight = source.height
        val x = (clamped.left * srcWidth).roundToInt().coerceIn(0, (srcWidth - 1).coerceAtLeast(0))
        val y = (clamped.top * srcHeight).roundToInt().coerceIn(0, (srcHeight - 1).coerceAtLeast(0))
        val width = ((clamped.right - clamped.left) * srcWidth)
            .roundToInt()
            .coerceIn(1, (srcWidth - x).coerceAtLeast(1))
        val height = ((clamped.bottom - clamped.top) * srcHeight)
            .roundToInt()
            .coerceIn(1, (srcHeight - y).coerceAtLeast(1))

        return Bitmap.createBitmap(source, x, y, width, height)
    }

    fun applyCropAndRotate(
        source: Bitmap,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect
    ): Bitmap {
        val rotated = rotateBitmap(source, rotationDegrees)
        return cropBitmap(rotated, cropRect)
    }

    suspend fun loadEditableBitmap(
        context: Context,
        uriString: String,
        maxDimensionPx: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = readBytesFromSource(context, uriString) ?: return@withContext null

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            var sampleSize = 1
            val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (longestSide / sampleSize > maxDimensionPx) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: return@withContext null

            val exifRotation = readExifRotationDegrees(bytes)
            if (exifRotation != 0) {
                rotateBitmap(decoded, exifRotation)
            } else {
                decoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load editable bitmap from $uriString: ${e.message}")
            null
        }
    }

    suspend fun saveEditedBitmapToAppStorage(
        context: Context,
        photoId: Long,
        bitmap: Bitmap
    ): SavedEditedImage = withContext(Dispatchers.IO) {
        val editedDir = File(context.filesDir, "edited_photos").apply {
            if (!exists()) mkdirs()
        }
        val outputFile = File(editedDir, "edited_${photoId}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            stream.flush()
        }

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider fallback to file URI: ${e.message}")
            Uri.fromFile(outputFile)
        }

        SavedEditedImage(
            uriString = uri.toString(),
            byteSize = outputFile.length()
        )
    }

    private fun readBytesFromSource(context: Context, uriString: String): ByteArray? {
        return if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            val connection = (URL(uriString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            try {
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        } else {
            val parsedUri = Uri.parse(uriString)
            val stream: InputStream? = if (parsedUri.scheme == "file") {
                parsedUri.path?.let { File(it).inputStream() }
            } else {
                context.contentResolver.openInputStream(parsedUri)
            }
            stream?.use { it.readBytes() }
        }
    }

    private fun readExifRotationDegrees(imageBytes: ByteArray): Int {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(imageBytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
