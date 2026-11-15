package com.example.data.local

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.data.model.MediaType
import com.example.data.model.Photo
import com.example.data.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MediaAccessState {
    GRANTED_ALL,
    GRANTED_PARTIAL,
    DENIED
}

class MediaStorePhotoLoader(private val context: Context) {

    companion object {
        private const val TAG = "MediaStorePhotoLoader"

        fun primaryReadPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }

        fun requiredPermissions(): Array<String> {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES
                )
                else -> arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }

        fun shouldShowRationale(activity: Activity): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                primaryReadPermission()
            )
        }

        fun openAppSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        tailrec fun findActivity(context: Context): Activity? = when (context) {
            is Activity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }

        fun checkMediaAccessState(context: Context): MediaAccessState {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    val hasAll = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasPartial = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) == PackageManager.PERMISSION_GRANTED

                    when {
                        hasAll -> MediaAccessState.GRANTED_ALL
                        hasPartial -> MediaAccessState.GRANTED_PARTIAL
                        else -> MediaAccessState.DENIED
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_IMAGES
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        MediaAccessState.GRANTED_ALL
                    } else {
                        MediaAccessState.DENIED
                    }
                }
                else -> {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        MediaAccessState.GRANTED_ALL
                    } else {
                        MediaAccessState.DENIED
                    }
                }
            }
        }
    }

    suspend fun loadLocalPhotos(limit: Int = 250): List<Photo> = withContext(Dispatchers.IO) {
        if (checkMediaAccessState(context) == MediaAccessState.DENIED) {
            return@withContext emptyList()
        }

        val photos = mutableListOf<Photo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val rawName = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val displayTitle = rawName
                        ?.substringBeforeLast('.')
                        ?.replace('_', ' ')
                        ?.replace('-', ' ')
                        ?.ifBlank { "Device Photo" }
                        ?: "Device Photo"

                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val timestamp = when {
                        dateTaken > 0L -> dateTaken
                        dateAddedSec > 0L -> dateAddedSec * 1000L
                        else -> System.currentTimeMillis()
                    }

                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol)?.trim().orEmpty() else ""

                    val mediaType = if (mimeType.equals("image/gif", ignoreCase = true)) {
                        MediaType.GIF
                    } else {
                        MediaType.IMAGE
                    }

                    val category = inferCategoryFromBucket(bucket)
                    val tags = buildList {
                        add("device")
                        add("local")
                        if (bucket.isNotBlank()) add(bucket.lowercase())
                        if (mediaType == MediaType.GIF) add("gif")
                    }.distinct().joinToString(", ")

                    photos.add(
                        Photo(
                            uri = contentUri.toString(),
                            title = displayTitle,
                            description = if (bucket.isNotBlank()) {
                                "Loaded from device MediaStore ($bucket album)."
                            } else {
                                "Loaded from device MediaStore."
                            },
                            category = category,
                            tags = tags,
                            timestamp = timestamp,
                            location = if (bucket.isNotBlank()) "Device · $bucket" else "Device Storage",
                            syncStatus = SyncStatus.LOCAL_ONLY,
                            storageSize = if (sizeBytes > 0L) sizeBytes else 1024L * 512L,
                            mediaType = mediaType
                        )
                    )
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to query MediaStore: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying MediaStore images: ${e.message}", e)
        }

        photos
    }

    suspend fun createPhotoFromPickedUri(uri: Uri): Photo = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some picker URIs are already readable without persistable grant
        }

        var displayName = "Picked Photo"
        var sizeBytes = 1024L * 512L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val raw = cursor.getString(nameIdx)
                        if (!raw.isNullOrBlank()) {
                            displayName = raw.substringBeforeLast('.').replace('_', ' ')
                        }
                    }
                    if (sizeIdx >= 0) {
                        val s = cursor.getLong(sizeIdx)
                        if (s > 0L) sizeBytes = s
                    }
                }
            }
        } catch (_: Exception) {
        }

        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val mediaType = if (mimeType.equals("image/gif", ignoreCase = true)) {
            MediaType.GIF
        } else {
            MediaType.IMAGE
        }

        Photo(
            uri = uri.toString(),
            title = displayName,
            description = "Selected from device using Android Photo Picker.",
            category = "Other",
            tags = "device, local, picked",
            timestamp = System.currentTimeMillis(),
            location = "Device Storage",
            syncStatus = SyncStatus.LOCAL_ONLY,
            storageSize = sizeBytes,
            mediaType = mediaType
        )
    }

    private fun inferCategoryFromBucket(bucket: String): String {
        val lower = bucket.lowercase()
        return when {
            lower.contains("nature") || lower.contains("landscape") -> "Nature"
            lower.contains("food") || lower.contains("meal") -> "Food"
            lower.contains("selfie") || lower.contains("portrait") || lower.contains("people") -> "People"
            lower.contains("travel") || lower.contains("trip") || lower.contains("vacation") -> "Places"
            lower.contains("screenshot") || lower.contains("download") -> "Things"
            else -> "Other"
        }
    }
}
