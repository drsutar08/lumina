package com.example.data.local

import androidx.room.*
import com.example.data.model.Photo
import com.example.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoritePhotos(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): Photo?

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    suspend fun getPhotoByUri(uri: String): Photo?

    @Query("SELECT * FROM photos WHERE category = :category ORDER BY timestamp DESC")
    fun getPhotosByCategory(category: String): Flow<List<Photo>>

    @Query("""
        SELECT * FROM photos 
        WHERE title LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%' 
        OR category LIKE '%' || :query || '%' 
        OR tags LIKE '%' || :query || '%' 
        ORDER BY timestamp DESC
    """)
    fun searchPhotos(query: String): Flow<List<Photo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo): Long

    @Update
    suspend fun updatePhoto(photo: Photo)

    @Delete
    suspend fun deletePhoto(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("SELECT * FROM photos WHERE syncStatus != :status")
    fun getPhotosNotSynced(status: SyncStatus = SyncStatus.SYNCED): Flow<List<Photo>>
    
    @Query("SELECT COUNT(*) FROM photos WHERE title LIKE '%' || :query || '%'")
    suspend fun getCountByTitle(query: String): Int

    @Query("SELECT * FROM photos WHERE syncStatus != :status")
    suspend fun getPhotosNotSyncedDirect(status: SyncStatus = SyncStatus.SYNCED): List<Photo>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getCount(): Int
}
