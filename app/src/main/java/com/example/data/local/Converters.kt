package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.MediaType
import com.example.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String {
        return value.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: Exception) {
            SyncStatus.LOCAL_ONLY
        }
    }

    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (e: Exception) {
            MediaType.IMAGE
        }
    }
}
