package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "photos")
data class Photo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String, // String representation of URI/URL
    val title: String,
    val description: String = "",
    val category: String = "Other", // Nature, People, Food, Places, Things, Other
    val tags: String = "", // Comma-separated search tags: "sunset, wave, ocean"
    val timestamp: Long = System.currentTimeMillis(),
    val location: String = "",
    val isFavorite: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val storageSize: Long = 1024 * 512, // simulated size in bytes
    val cloudUrl: String? = null, // if syncStatus = SYNCED
    val mediaType: MediaType = MediaType.IMAGE,
    val duration: Long = 0L // video duration in milliseconds, 0 if not a video
) : Serializable

enum class SyncStatus {
    LOCAL_ONLY, // Not synced
    SYNC_PENDING, // Sync action queued
    SYNCED // Cloud backed up
}

enum class MediaType {
    IMAGE,
    VIDEO,
    GIF
}
