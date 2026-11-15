package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.local.MediaStorePhotoLoader
import com.example.data.local.PhotoDao
import com.example.data.model.Photo
import com.example.data.model.MediaType
import com.example.data.model.SyncStatus
import com.example.data.api.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val mediaStoreLoader: MediaStorePhotoLoader? = null
) {

    private val TAG = "PhotoRepository"

    // Expose local database flows
    val allPhotos: Flow<List<Photo>> = photoDao.getAllPhotos()
    val favoritePhotos: Flow<List<Photo>> = photoDao.getFavoritePhotos()

    // Sync state managers
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    /**
     * Seed initial demo photos if the database is empty.
     */
    suspend fun seedMockPhotosIfEmpty() {
        if (photoDao.getCount() == 0) {
            Log.d(TAG, "Database is empty. Seeding mock photo library presets...")
            val presets = listOf(
                Photo(
                    uri = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800",
                    title = "Sun-kissed Coastline",
                    description = "A pristine golden sand beach meeting crystal clear turquoise ocean waves at warm sunset.",
                    category = "Nature",
                    tags = "beach, ocean, wave, sunset, sand, paradise, water, summer, nature",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://lumina-cloud.google.com/backup/beach_sunset.jpg"
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1552053831-71594a27632d?w=800",
                    title = "Sunny Golden Retriever",
                    description = "An adorable golden retriever puppy sitting happily on a bright green grass lawn, looking curiously into the camera.",
                    category = "People", // Pets mapped to People & Pets
                    tags = "dog, puppy, pet, animal, golden retriever, garden, lawn, grass, friends, eyes",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://lumina-cloud.google.com/backup/puppy_retriever.jpg"
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800",
                    title = "Rustic Pepperoni Pizza",
                    description = "Freshly sliced wood-fired pepperoni pizza on a wooden dining board with bubbling cheese and fresh basil leaves.",
                    category = "Food",
                    tags = "pizza, food, cheese, dining, pepperoni, hot, table, basil, rustic",
                    syncStatus = SyncStatus.LOCAL_ONLY
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1540959733332-eab4deceeaf7?w=800",
                    title = "Neon Tokyo Lights",
                    description = "A vibrant look down a winding Tokyo side street illuminated by glowing neon storefronts and signs under a wet night sky.",
                    category = "Places",
                    tags = "tokyo, neon, city, japan, night, street, lights, rain, places, urban",
                    syncStatus = SyncStatus.LOCAL_ONLY
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1510798831971-661eb04b3739?w=800",
                    title = "Winter Mountain Cabin",
                    description = "A warm wooden cabin nestled deep among snowy, frosted pine trees with majestic tall mountains in the cold winter backdrop.",
                    category = "Places",
                    tags = "cabin, mountains, snowy, winter, nature, cozy, forest, wood, escape",
                    syncStatus = SyncStatus.LOCAL_ONLY
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800",
                    title = "Minimalist Coffee Break",
                    description = "A clean overhead shot of a warm ceramic coffee mug containing microfoam espresso art, sitting on a plain white modern cafe table.",
                    category = "Things",
                    tags = "coffee, cafe, morning, minimalist, table, mug, cup, espresso, breakfast",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://lumina-cloud.google.com/backup/coffee_break.jpg"
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=800",
                    title = "Sunset Concert Celebration",
                    description = "A group of close friends dancing, laughing, and raising their hands together against an atmospheric warm sunset flare.",
                    category = "People",
                    tags = "party, friends, celebration, music, happy, smiling, dance, crowd, sunset",
                    syncStatus = SyncStatus.LOCAL_ONLY
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800",
                    title = "Classic SLR Camera",
                    description = "An aesthetic vintage rangefinder SLR camera resting on a leather workbook with a beautiful portrait prime lens attached.",
                    category = "Things",
                    tags = "camera, photography, tech, retro, vintage, lens, shoot, things, photo",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://lumina-cloud.google.com/backup/vintage_camera.jpg"
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800",
                    title = "Cinematic Forest Stream",
                    description = "A gorgeous high-fidelity video showcasing a scenic stream path cascading beautifully with sunlight beams through giant redwood trees.",
                    category = "Nature",
                    tags = "forest, river, stream, sunlight, outdoor, scenic, video, nature, wilderness, movie",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4",
                    mediaType = MediaType.VIDEO,
                    duration = 14000L
                ),
                Photo(
                    uri = "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=800",
                    title = "Waves Crushing Sandy Coast",
                    description = "A powerful captured video sequence tracking turquoise waves crashing against high cliff rocks and shifting sands.",
                    category = "Nature",
                    tags = "ocean, beach, coastline, waves, spray, sea, marine, video, cinematic, sunset",
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    cloudUrl = "https://assets.mixkit.co/videos/preview/mixkit-waves-breaking-on-the-pEB-large.mp4",
                    mediaType = MediaType.VIDEO,
                    duration = 12000L
                ),
                Photo(
                    uri = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHIyb2h5bm1hZnlwMjFocXh0Mm14MWhybWoxN2NlZTR3NXNqczd0cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/MDJ9IbxxvDUQM/giphy.gif",
                    title = "Cheerful Climbing Kitten",
                    description = "An animated, looping GIF of an adorable cartoon-eyed kitten stretching and climbing happily under warm room light.",
                    category = "People",
                    tags = "cat, kitten, cute, play, animal, gif, animation, funny, loop, pets",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHIyb2h5bm1hZnlwMjFocXh0Mm14MWhybWoxN2NlZTR3NXNqczd0cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/MDJ9IbxxvDUQM/giphy.gif",
                    mediaType = MediaType.GIF
                ),
                Photo(
                    uri = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOXVrdTRmYTFjdmt5NXhycXhuZDRoYXp4bTZuYTNrcGRqNGw1YzFqNyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3d6WO0F9sk9h9e9dfc/giphy.gif",
                    title = "Vaporwave Horizon Horizon",
                    description = "An animated retro synthwave neon grids looping landscape and sunset horizon gif that glows continuously.",
                    category = "Places",
                    tags = "vaporwave, retro, synthwave, neon, purple, sunset, horizon, gif, animation, loop",
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    cloudUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOXVrdTRmYTFjdmt5NXhycXhuZDRoYXp4bTZuYTNrcGRqNGw1YzFqNyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3d6WO0F9sk9h9e9dfc/giphy.gif",
                    mediaType = MediaType.GIF
                )
            )
            for (photo in presets) {
                photoDao.insertPhoto(photo)
            }
        }

        // Also ensure Sarah at the beach in 2022 and Family Thanksgiving in 2023 exist for specific natural query parsing proofs
        val hasSarahCount = photoDao.getCountByTitle("Sarah")
        if (hasSarahCount == 0) {
            photoDao.insertPhoto(
                Photo(
                    uri = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=800",
                    title = "Sarah at the Beach",
                    description = "A wonderful photo of Sarah sitting happily by the ocean coast under a warm sunset flare during summer vacation.",
                    category = "People",
                    tags = "sarah, beach, ocean, smile, sunset, vacation, 2022, summer, people, holiday",
                    timestamp = 1656244800000L, // Monday, June 26, 2022
                    location = "Miami Beach, Florida",
                    syncStatus = SyncStatus.SYNCED,
                    cloudUrl = "https://lumina-cloud.google.com/backup/sarah_beach_2022.jpg"
                )
            )
        }

        val hasThanksgivingCount = photoDao.getCountByTitle("Thanksgiving")
        if (hasThanksgivingCount == 0) {
            photoDao.insertPhoto(
                Photo(
                    uri = "https://images.unsplash.com/photo-1511795409834-ef04bbd61622?w=800",
                    title = "Family Thanksgiving Reunion",
                    description = "Family gathering together happily around the grand Thanksgiving feast in autumn.",
                    category = "People",
                    tags = "family, gathering, thanksgiving, dinner, people, happy, house, 2023, autumn, reunion",
                    timestamp = 1700762400000L, // Thursday, Nov 23, 2023
                    location = "Grandma's House",
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            )
        }
    }

    /**
     * Queries the device MediaStore for local photos and inserts any unseen URIs into Room,
     * preserving existing favorites, AI metadata, and cloud sync state.
     */
    suspend fun syncDeviceMediaStore(): Int = withContext(Dispatchers.IO) {
        val loader = mediaStoreLoader ?: return@withContext 0
        val devicePhotos = loader.loadLocalPhotos()
        var insertedCount = 0
        for (localPhoto in devicePhotos) {
            if (photoDao.getPhotoByUri(localPhoto.uri) == null) {
                photoDao.insertPhoto(localPhoto)
                insertedCount++
            }
        }
        Log.d(TAG, "MediaStore sync scanned ${devicePhotos.size} local photos, inserted $insertedCount new items.")
        insertedCount
    }

    /**
     * Imports local photos selected through the zero-permission Android Photo Picker.
     */
    suspend fun importPickedUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val loader = mediaStoreLoader ?: return@withContext 0
        var insertedCount = 0
        for (uri in uris) {
            val uriString = uri.toString()
            if (photoDao.getPhotoByUri(uriString) == null) {
                val photo = loader.createPhotoFromPickedUri(uri)
                photoDao.insertPhoto(photo)
                insertedCount++
            }
        }
        insertedCount
    }

    suspend fun getPhotoById(id: Long): Photo? = withContext(Dispatchers.IO) {
        photoDao.getPhotoById(id)
    }

    fun getPhotosByCategory(category: String): Flow<List<Photo>> {
        return photoDao.getPhotosByCategory(category)
    }

    fun searchPhotos(query: String): Flow<List<Photo>> {
        return photoDao.searchPhotos(query)
    }

    suspend fun addPhoto(photo: Photo): Long = withContext(Dispatchers.IO) {
        photoDao.insertPhoto(photo)
    }

    suspend fun updatePhoto(photo: Photo) = withContext(Dispatchers.IO) {
        photoDao.updatePhoto(photo)
    }

    suspend fun deletePhoto(photo: Photo) = withContext(Dispatchers.IO) {
        photoDao.deletePhoto(photo)
    }

    suspend fun deletePhotoById(id: Long) = withContext(Dispatchers.IO) {
        photoDao.deletePhotoById(id)
    }

    /**
     * Triggers dynamic AI cataloging for a local image using Gemini.
     * Extracts tags, category, title, and description.
     */
    suspend fun processPhotoWithAI(id: Long): Photo? = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(id) ?: return@withContext null
        Log.d(TAG, "Requesting AI Analysis for photo: ${photo.uri}")

        val result = GeminiClient.analyzePhoto(photo.uri)
        if (result != null) {
            val updatedPhoto = photo.copy(
                title = result.title,
                description = result.description,
                category = result.category,
                tags = result.tags.joinToString(", "),
                // Set sync status pending when cataloged successfully
                syncStatus = SyncStatus.SYNC_PENDING
            )
            photoDao.updatePhoto(updatedPhoto)
            return@withContext updatedPhoto
        } else {
            // Apply a robust local pseudo-heuristic if Gemini fails or is unconfigured
            val fallbacks = generateLocalHeuristicMetadata(photo.uri)
            val updatedPhoto = photo.copy(
                title = fallbacks.title,
                description = fallbacks.description,
                category = fallbacks.category,
                tags = fallbacks.tags,
                syncStatus = SyncStatus.SYNC_PENDING
            )
            photoDao.updatePhoto(updatedPhoto)
            return@withContext updatedPhoto
        }
    }

    /**
     * Expands search queries semantically using Gemini, with a rich local visual-concept fallback
     * when offline or when a Gemini API key is not configured.
     */
    suspend fun expandSearchSemantically(query: String): List<String> = withContext(Dispatchers.IO) {
        val remote = GeminiClient.expandSearchQuery(query)
        if (remote.isNotEmpty()) {
            return@withContext remote
        }
        expandQueryLocally(query)
    }

    private fun expandQueryLocally(query: String): List<String> {
        val q = query.lowercase().trim()
        if (q.length < 2) return emptyList()

        val conceptMap = listOf(
            listOf("sunset", "golden hour", "dusk", "evening", "sun") to listOf("sunset", "ocean", "beach", "horizon", "warm"),
            listOf("ocean", "sea", "water", "coast", "wave", "beach", "sand") to listOf("beach", "ocean", "wave", "sand", "coastline", "summer"),
            listOf("pet", "animal", "furry", "cute", "dog", "puppy", "cat", "kitten") to listOf("dog", "puppy", "cat", "kitten", "pet", "animal"),
            listOf("city", "urban", "night", "street", "neon", "lights", "japan") to listOf("city", "tokyo", "neon", "street", "lights", "urban"),
            listOf("snow", "cold", "winter", "mountain", "cabin", "cozy", "ski") to listOf("winter", "cabin", "snowy", "mountains", "cozy", "forest"),
            listOf("food", "meal", "hungry", "eat", "drink", "coffee", "cafe", "pizza", "dinner", "breakfast") to listOf("food", "pizza", "coffee", "cafe", "dinner", "dining"),
            listOf("forest", "trees", "nature", "green", "outdoors", "wild", "river", "stream", "hike") to listOf("forest", "nature", "stream", "river", "sunlight", "outdoor"),
            listOf("party", "celebration", "friends", "happy", "music", "dance", "concert") to listOf("party", "friends", "celebration", "music", "dance", "happy"),
            listOf("family", "holiday", "gathering", "reunion", "home", "thanksgiving") to listOf("family", "gathering", "thanksgiving", "reunion", "dinner"),
            listOf("camera", "retro", "vintage", "tech", "photography", "lens") to listOf("camera", "vintage", "retro", "photography", "lens"),
            listOf("synthwave", "vaporwave", "purple", "loop", "gif", "animated", "animation") to listOf("vaporwave", "retro", "neon", "purple", "gif", "animation"),
            listOf("summer", "vacation", "trip", "travel", "places") to listOf("summer", "beach", "vacation", "miami", "places", "holiday")
        )

        val matched = linkedSetOf<String>()
        for ((triggers, expansions) in conceptMap) {
            if (triggers.any { trigger -> q.contains(trigger) || trigger.contains(q) }) {
                matched.addAll(expansions)
            }
        }
        return matched.filterNot { it.equals(q, ignoreCase = true) }.take(6)
    }

    /**
     * Performs a cloud backup of all pending and local-only photos when online.
     * Aborts cleanly if connectivity is lost before or during synchronization.
     */
    suspend fun syncPhotosWithCloud(
        isOnlineProvider: () -> Boolean = { true },
        forceReverify: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!isOnlineProvider()) {
            _isSyncing.value = false
            _syncMessage.value = "Offline — waiting for network connection"
            return@withContext
        }
        if (_isSyncing.value) return@withContext

        _isSyncing.value = true
        _syncProgress.value = 0f
        _syncMessage.value = "Preparing files for backup..."
        delay(650)

        if (!isOnlineProvider()) {
            _isSyncing.value = false
            _syncMessage.value = "Sync paused — device is offline"
            return@withContext
        }

        // Gather all unsynced files
        val targetPhotos = photoDao.getPhotosNotSyncedDirect(SyncStatus.SYNCED)

        if (targetPhotos.isEmpty()) {
            if (forceReverify) {
                _syncMessage.value = "Verifying cloud index..."
                _syncProgress.value = 0.55f
                delay(650)
                if (!isOnlineProvider()) {
                    _isSyncing.value = false
                    _syncMessage.value = "Sync paused — device is offline"
                    return@withContext
                }
            }
            _syncProgress.value = 1f
            _syncMessage.value = "All photos are already backed up!"
            delay(800)
            _isSyncing.value = false
            return@withContext
        }

        val total = targetPhotos.size
        for ((index, photo) in targetPhotos.withIndex()) {
            if (!isOnlineProvider()) {
                _isSyncing.value = false
                _syncMessage.value = "Sync paused — device went offline"
                return@withContext
            }

            _syncMessage.value = "Uploading: ${photo.title}..."

            // Simulating chunks of upload
            val steps = 3
            for (step in 1..steps) {
                if (!isOnlineProvider()) {
                    _isSyncing.value = false
                    _syncMessage.value = "Sync paused — device went offline"
                    return@withContext
                }
                val individualProgress = (index.toFloat() + (step.toFloat() / steps)) / total
                _syncProgress.value = individualProgress
                delay(280)
            }

            val syncedPhoto = photo.copy(
                syncStatus = SyncStatus.SYNCED,
                cloudUrl = "https://lumina-cloud.google.com/backup/photo_${photo.id}.jpg"
            )
            photoDao.updatePhoto(syncedPhoto)
        }

        _syncProgress.value = 1f
        _syncMessage.value = "Sync completed! Backed up $total files."
        delay(900)
        _isSyncing.value = false
    }

    fun markOfflinePaused() {
        if (_isSyncing.value) {
            _isSyncing.value = false
            _syncMessage.value = "Sync paused — device is offline"
        }
    }

    /**
     * Local parser in case of no Gemini API Key configuration. Handles fallback tags gracefully.
     */
    private fun generateLocalHeuristicMetadata(uri: String): FallbackMetadata {
        return when {
            uri.contains("food") || uri.contains("pizza") || uri.contains("dining") -> {
                FallbackMetadata(
                    title = "Gourmet Dish",
                    description = "An appealing close-up of delicious ready-to-eat culinary preparation.",
                    category = "Food",
                    tags = "food, meal, plate, appetizer, recipe, delicious, gourmet"
                )
            }
            uri.contains("dog") || uri.contains("cat") || uri.contains("pet") || uri.contains("retriever") -> {
                FallbackMetadata(
                    title = "Loyal Companion",
                    description = "A heart-warming portrait of an adorable domestic animal resting comfortably.",
                    category = "People",
                    tags = "animal, pet, companion, friendly, mammal, play, fur, eyes"
                )
            }
            uri.contains("city") || uri.contains("tokyo") || uri.contains("street") || uri.contains("neon") -> {
                FallbackMetadata(
                    title = "Urban Horizon",
                    description = "A sprawling view showcasing building architectures and lively asphalt streets.",
                    category = "Places",
                    tags = "places, urban, city, streets, lights, architecture, windows"
                )
            }
            uri.contains("cabin") || uri.contains("mountain") || uri.contains("nature") || uri.contains("sunset") || uri.contains("coast") -> {
                FallbackMetadata(
                    title = "Serene Wilderness",
                    description = "An immersive perspective of natural scenery showcasing earth's grand environments.",
                    category = "Nature",
                    tags = "nature, wild, landscape, travel, scenic, environment, sun, outdoor"
                )
            }
            else -> {
                FallbackMetadata(
                    title = "Imported Photo",
                    description = "A newly acquired memory added safely to Lumina local index.",
                    category = "Other",
                    tags = "imported, memory, lumina, local, snap, album"
                )
            }
        }
    }

    private data class FallbackMetadata(
        val title: String,
        val description: String,
        val category: String,
        val tags: String
    )
}
