package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ads.AdConsentState
import com.example.data.ads.AdPolicyRules
import com.example.data.ads.AdPreferencesRepository
import com.example.data.local.ImageEditProcessor
import com.example.data.local.MediaAccessState
import com.example.data.local.MediaStorePhotoLoader
import com.example.data.local.NormalizedCropRect
import com.example.data.local.PhotoDatabase
import com.example.data.model.Photo
import com.example.data.model.SyncStatus
import com.example.data.network.NetworkConnectivityObserver
import com.example.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotosViewModel(
    application: Application,
    private val repository: PhotoRepository
) : AndroidViewModel(application) {

    private val TAG = "PhotosViewModel"

    // Raw photo collection state
    val allPhotos: StateFlow<List<Photo>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritePhotos: StateFlow<List<Photo>> = repository.favoritePhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Connectivity & Cloud Sync indicator states
    private val connectivityObserver = NetworkConnectivityObserver(application)

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = connectivityObserver.isConnectedNow()
        )

    // Sync status states
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val syncProgress: StateFlow<Float> = repository.syncProgress
    val syncMessage: StateFlow<String> = repository.syncMessage

    val cloudSyncState: StateFlow<CloudSyncIndicatorState> = combine(
        isOnline,
        isSyncing
    ) { online, syncing ->
        when {
            !online -> CloudSyncIndicatorState.OFFLINE
            syncing -> CloudSyncIndicatorState.SYNCING
            else -> CloudSyncIndicatorState.SYNCED
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = if (connectivityObserver.isConnectedNow()) {
            CloudSyncIndicatorState.SYNCED
        } else {
            CloudSyncIndicatorState.OFFLINE
        }
    )

    private val _isAutoBackupEnabled = MutableStateFlow(false)
    val isAutoBackupEnabled = _isAutoBackupEnabled.asStateFlow()

    fun setAutoBackupEnabled(enabled: Boolean) {
        _isAutoBackupEnabled.value = enabled
        if (enabled) {
            triggerBackupSync()
        }
    }

    // Search query states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All") // All, Nature, People, Food, Places, Things, Other
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Semantic search feedback
    private val _isExpandingSearch = MutableStateFlow(false)
    val isExpandingSearch: StateFlow<Boolean> = _isExpandingSearch.asStateFlow()

    private val _expandedTags = MutableStateFlow<List<String>>(emptyList())
    val expandedTags: StateFlow<List<String>> = _expandedTags.asStateFlow()

    // Screen states & detail focus
    private val _selectedPhoto = MutableStateFlow<Photo?>(null)
    val selectedPhoto: StateFlow<Photo?> = _selectedPhoto.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isSavingEdit = MutableStateFlow(false)
    val isSavingEdit: StateFlow<Boolean> = _isSavingEdit.asStateFlow()

    // Slideshow playback & configuration state
    private val slideshowPrefs by lazy {
        getApplication<Application>().getSharedPreferences("lumina_slideshow_prefs", Context.MODE_PRIVATE)
    }

    private val _isSlideshowPlaying = MutableStateFlow(false)
    val isSlideshowPlaying: StateFlow<Boolean> = _isSlideshowPlaying.asStateFlow()

    private val _slideshowIntervalSeconds = MutableStateFlow(loadSlideshowInterval())
    val slideshowIntervalSeconds: StateFlow<Int> = _slideshowIntervalSeconds.asStateFlow()

    private val _slideshowTransition = MutableStateFlow(loadSlideshowTransition())
    val slideshowTransition: StateFlow<SlideshowTransition> = _slideshowTransition.asStateFlow()

    private val _slideshowLoopEnabled = MutableStateFlow(loadSlideshowLoop())
    val slideshowLoopEnabled: StateFlow<Boolean> = _slideshowLoopEnabled.asStateFlow()

    private val _slideshowDirection = MutableStateFlow(1)
    val slideshowDirection: StateFlow<Int> = _slideshowDirection.asStateFlow()

    private fun loadSlideshowInterval(): Int {
        return slideshowPrefs.getInt("interval_seconds", 4).coerceIn(2, 15)
    }

    private fun loadSlideshowTransition(): SlideshowTransition {
        val raw = slideshowPrefs.getString("transition_style", SlideshowTransition.CROSSFADE.name)
        return SlideshowTransition.entries.firstOrNull { it.name == raw } ?: SlideshowTransition.CROSSFADE
    }

    private fun loadSlideshowLoop(): Boolean {
        return slideshowPrefs.getBoolean("loop_enabled", true)
    }

    // Device MediaStore access state and loading flag
    private val _mediaAccessState = MutableStateFlow(
        MediaStorePhotoLoader.checkMediaAccessState(application)
    )
    val mediaAccessState: StateFlow<MediaAccessState> = _mediaAccessState.asStateFlow()

    private val _isLoadingMediaStore = MutableStateFlow(false)
    val isLoadingMediaStore: StateFlow<Boolean> = _isLoadingMediaStore.asStateFlow()

    private val _mediaScanFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val mediaScanFeedback: SharedFlow<String> = _mediaScanFeedback.asSharedFlow()

    val devicePhotoCount: StateFlow<Int> = allPhotos
        .map { photos -> photos.count { it.uri.startsWith("content://") } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val adPreferencesRepository by lazy {
        AdPreferencesRepository(getApplication())
    }

    val adMonetizationState: StateFlow<AdMonetizationUiState> = combine(
        adPreferencesRepository.consentState,
        adPreferencesRepository.isAdFreeEnabled,
        adPreferencesRepository.bonusCloudStorageBytes,
        adPreferencesRepository.lastInterstitialShownAtMs,
        adPreferencesRepository.completedExportsCount
    ) { consent, isAdFree, bonusBytes, lastInterstitialMs, exportsCount ->
        AdMonetizationUiState(
            consentState = consent,
            isAdFreeEnabled = isAdFree,
            bonusCloudStorageBytes = bonusBytes,
            maxBonusCloudStorageBytes = AdPolicyRules.MAX_BONUS_STORAGE_BYTES,
            shouldShowAds = AdPolicyRules.shouldShowAds(consent, isAdFree),
            shouldPromptConsent = consent == AdConsentState.UNKNOWN,
            lastInterstitialShownAtMs = lastInterstitialMs,
            completedExportsCount = exportsCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AdMonetizationUiState(
            consentState = adPreferencesRepository.consentState.value,
            isAdFreeEnabled = adPreferencesRepository.isAdFreeEnabled.value,
            bonusCloudStorageBytes = adPreferencesRepository.bonusCloudStorageBytes.value,
            maxBonusCloudStorageBytes = AdPolicyRules.MAX_BONUS_STORAGE_BYTES,
            shouldShowAds = AdPolicyRules.shouldShowAds(
                adPreferencesRepository.consentState.value,
                adPreferencesRepository.isAdFreeEnabled.value
            ),
            shouldPromptConsent = adPreferencesRepository.consentState.value == AdConsentState.UNKNOWN,
            lastInterstitialShownAtMs = adPreferencesRepository.lastInterstitialShownAtMs.value,
            completedExportsCount = adPreferencesRepository.completedExportsCount.value
        )
    )

    private val virtualAlbumPrefs by lazy {
        getApplication<Application>().getSharedPreferences("lumina_virtual_albums", Context.MODE_PRIVATE)
    }

    private val _customAlbumTags = MutableStateFlow(loadStoredCustomTags())
    val customAlbumTags: StateFlow<List<String>> = _customAlbumTags.asStateFlow()

    private val _galleryGroupingMode = MutableStateFlow(GalleryGroupingMode.NONE)
    val galleryGroupingMode: StateFlow<GalleryGroupingMode> = _galleryGroupingMode.asStateFlow()

    private val _selectedVirtualAlbumId = MutableStateFlow<String?>(null)
    val selectedVirtualAlbumId: StateFlow<String?> = _selectedVirtualAlbumId.asStateFlow()

    // Multi-select gallery state
    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedPhotoIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private fun loadStoredCustomTags(): List<String> {
        val raw = virtualAlbumPrefs.getStringSet("custom_tags", emptySet()) ?: emptySet()
        return raw.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    private fun persistCustomTags(tags: Set<String>) {
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        virtualAlbumPrefs.edit().putStringSet("custom_tags", clean).apply()
        _customAlbumTags.value = clean.sorted()
    }

    // Filtered photos block based on Search, Category, and Tag expansions with Natural Language Heuristics
    val filteredPhotos: StateFlow<List<Photo>> = combine(
        allPhotos,
        searchQuery,
        selectedCategory,
        expandedTags
    ) { photos, query, category, expanded ->
        var list = photos

        // Filter by category pill first
        if (category == "Device") {
            list = list.filter { it.uri.startsWith("content://") }
        } else if (category != "All") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        // Filter by search query or semantic expanded keywords
        if (query.isNotBlank()) {
            // Extract a year if present (e.g. 2022, 2023)
            val yearRegex = "\\b(201\\d|202\\d|203\\d)\\b".toRegex()
            val yearMatch = yearRegex.find(query)
            val queriedYear = yearMatch?.value?.toIntOrNull()
            
            // Remove the year and common fill words like "photos of", "at the", "in", "during", "at" to isolate main tags
            var cleanQuery = query
                .replace(yearRegex, "")
                .replace("\\b(photos of|photos|images of|images|pictures of|pictures)\\b".toRegex(RegexOption.IGNORE_CASE), "")
                .replace("\\b(at the|in the|on the|at|in|on|with|during|of)\\b".toRegex(RegexOption.IGNORE_CASE), "")
                .replace("\\s+".toRegex(), " ")
                .trim()

            val queryKeywords = cleanQuery.split(" ", ",").filter { it.length > 1 }

            list = list.filter { photo ->
                val matchesYear = if (queriedYear != null) {
                    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    calendar.timeInMillis = photo.timestamp
                    val photoYear = calendar.get(java.util.Calendar.YEAR)
                    photoYear == queriedYear
                } else {
                    true
                }

                val matchesKeywords = if (queryKeywords.isNotEmpty()) {
                    queryKeywords.all { keyword ->
                        photo.title.contains(keyword, ignoreCase = true) ||
                        photo.description.contains(keyword, ignoreCase = true) ||
                        photo.category.contains(keyword, ignoreCase = true) ||
                        photo.tags.contains(keyword, ignoreCase = true) ||
                        photo.location.contains(keyword, ignoreCase = true)
                    }
                } else {
                    true
                }

                val matchesBasicQuery = photo.title.contains(query, ignoreCase = true) ||
                        photo.description.contains(query, ignoreCase = true) ||
                        photo.category.contains(query, ignoreCase = true) ||
                        photo.tags.contains(query, ignoreCase = true) ||
                        photo.location.contains(query, ignoreCase = true)

                val matchesExpandedTags = expanded.any { tag ->
                    photo.tags.contains(tag, ignoreCase = true) ||
                        photo.title.contains(tag, ignoreCase = true) ||
                        photo.description.contains(tag, ignoreCase = true) ||
                        photo.category.contains(tag, ignoreCase = true)
                }

                matchesYear && (matchesKeywords || matchesBasicQuery || matchesExpandedTags)
            }
        }

        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Ordered list of image/GIF items eligible for slideshow playback in the viewer
    val slideshowPhotos: StateFlow<List<Photo>> = combine(
        allPhotos,
        filteredPhotos,
        selectedPhoto
    ) { all, filtered, current ->
        val filteredImages = filtered.filter { it.mediaType != com.example.data.model.MediaType.VIDEO }
        val allImages = all.filter { it.mediaType != com.example.data.model.MediaType.VIDEO }
        when {
            current != null && filteredImages.any { it.id == current.id } -> filteredImages
            filteredImages.isNotEmpty() && current == null -> filteredImages
            else -> allImages
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // AI Suggested Smart Albums (Themes, Locations, and Dates)
    val suggestedAlbums: StateFlow<List<SuggestedAlbum>> = allPhotos.map { photos ->
        val albums = mutableListOf<SuggestedAlbum>()
        
        // 1. Group by locations (e.g. Miami, Tokyo, Grandma's House)
        val locationGroups = photos.filter { it.location.isNotBlank() }
            .groupBy { it.location.split(",").first().trim() }
        for ((locName, locPhotos) in locationGroups) {
            val cover = locPhotos.firstOrNull()?.uri ?: ""
            albums.add(
                SuggestedAlbum(
                    id = "loc_$locName",
                    title = "Trip to $locName",
                    subtitle = "${locPhotos.size} item${if (locPhotos.size > 1) "s" else ""} • Vacation Spot",
                    searchQuery = locName,
                    coverPhotoUrl = cover,
                    type = "LOCATION"
                )
            )
        }

        // 2. Group by calendar year
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val yearGroups = photos.groupBy {
            calendar.timeInMillis = it.timestamp
            calendar.get(java.util.Calendar.YEAR)
        }
        for ((year, yearPhotos) in yearGroups) {
            val cover = yearPhotos.firstOrNull()?.uri ?: ""
            albums.add(
                SuggestedAlbum(
                    id = "year_$year",
                    title = "Chronicle: Year $year",
                    subtitle = "${yearPhotos.size} moment${if (yearPhotos.size > 1) "s" else ""} • Captured",
                    searchQuery = year.toString(),
                    coverPhotoUrl = cover,
                    type = "DATE"
                )
            )
        }

        // 3. Theme groupings based on popular tags (Sunset, Beach, Pets, Food, Outdoors)
        val beachPhotos = photos.filter { it.tags.contains("beach", ignoreCase = true) }
        if (beachPhotos.isNotEmpty()) {
            albums.add(
                SuggestedAlbum(
                    id = "theme_beach",
                    title = "Sunny Coastlines",
                    subtitle = "${beachPhotos.size} dynamic coast views",
                    searchQuery = "beach",
                    coverPhotoUrl = beachPhotos.first().uri,
                    type = "THEME"
                )
            )
        }

        val petPhotos = photos.filter { it.tags.contains("dog", ignoreCase = true) || it.tags.contains("pet", ignoreCase = true) }
        if (petPhotos.isNotEmpty()) {
            albums.add(
                SuggestedAlbum(
                    id = "theme_pets",
                    title = "Life with Pets",
                    subtitle = "${petPhotos.size} animal companion snaps",
                    searchQuery = "pet",
                    coverPhotoUrl = petPhotos.first().uri,
                    type = "THEME"
                )
            )
        }

        val foodPhotos = photos.filter { it.category.equals("food", ignoreCase = true) || it.tags.contains("pizza", ignoreCase = true) }
        if (foodPhotos.isNotEmpty()) {
            albums.add(
                SuggestedAlbum(
                    id = "theme_food",
                    title = "Gourmet Creations",
                    subtitle = "${foodPhotos.size} delicious plates",
                    searchQuery = "pizza",
                    coverPhotoUrl = foodPhotos.first().uri,
                    type = "THEME"
                )
            )
        }

        albums
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Storage info calculation (includes dynamic rewarded cloud quota bonus)
    val storageInfo: StateFlow<StorageStats> = combine(
        allPhotos,
        adPreferencesRepository.bonusCloudStorageBytes
    ) { photos, bonusBytes ->
        val syncedPhotos = photos.filter { it.syncStatus == SyncStatus.SYNCED }
        val unsyncedPhotos = photos.filter { it.syncStatus != SyncStatus.SYNCED }
        val syncedSize = syncedPhotos.sumOf { it.storageSize }
        val localSize = unsyncedPhotos.sumOf { it.storageSize }
        val totalLibraryBytes = syncedSize + localSize

        val imageItems = photos.filter { it.mediaType == com.example.data.model.MediaType.IMAGE }
        val videoItems = photos.filter { it.mediaType == com.example.data.model.MediaType.VIDEO }
        val gifItems = photos.filter { it.mediaType == com.example.data.model.MediaType.GIF }

        val syncedImageBytes = syncedPhotos
            .filter { it.mediaType == com.example.data.model.MediaType.IMAGE }
            .sumOf { it.storageSize }
        val syncedVideoBytes = syncedPhotos
            .filter { it.mediaType == com.example.data.model.MediaType.VIDEO }
            .sumOf { it.storageSize }

        // Base 15 GB standard free storage + any earned Unity LevelPlay rewarded bonus (up to +10 GB)
        val cloudLimit = AdPolicyRules.BASE_CLOUD_LIMIT_BYTES + bonusBytes
        val usedCloudBytes = 2L * 1024 * 1024 * 1024 + syncedSize // base 2GB simulated docs/backups + synced media
        val percentage = (usedCloudBytes.toFloat() / cloudLimit.toFloat()).coerceIn(0f, 1f)

        val (deviceTotalBytes, deviceAvailableBytes) = readDeviceInternalStorageStats()

        StorageStats(
            usedBytesCloud = usedCloudBytes,
            totalLimitCloud = cloudLimit,
            bonusCloudBytes = bonusBytes,
            localUnsyncedBytes = localSize,
            localUnsyncedCount = unsyncedPhotos.size,
            syncedCount = syncedPhotos.size,
            totalPhotoCount = photos.size,
            totalLibraryBytes = totalLibraryBytes,
            photosBytes = imageItems.sumOf { it.storageSize },
            photosCount = imageItems.size,
            videosBytes = videoItems.sumOf { it.storageSize },
            videosCount = videoItems.size,
            gifsAndDocsBytes = gifItems.sumOf { it.storageSize },
            gifsAndDocsCount = gifItems.size,
            syncedPhotosBytes = syncedImageBytes,
            syncedVideosBytes = syncedVideoBytes,
            deviceTotalBytes = deviceTotalBytes,
            deviceAvailableBytes = deviceAvailableBytes,
            percentUsed = percentage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StorageStats()
    )

    private fun readDeviceInternalStorageStats(): Pair<Long, Long> {
        return try {
            val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = statFs.totalBytes.coerceAtLeast(1L)
            val available = statFs.availableBytes.coerceIn(0L, total)
            total to available
        } catch (_: Exception) {
            (64L * 1024 * 1024 * 1024) to (32L * 1024 * 1024 * 1024)
        }
    }

    // Virtual albums grouped by Month & Year
    val dateVirtualAlbums: StateFlow<List<VirtualAlbum>> = allPhotos.map { photos ->
        buildDateVirtualAlbums(photos)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Virtual albums grouped by custom tags and indexed photo tags
    val tagVirtualAlbums: StateFlow<List<VirtualAlbum>> = combine(
        allPhotos,
        customAlbumTags
    ) { photos, customTags ->
        buildTagVirtualAlbums(photos, customTags)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Grouped gallery sections for the main Photos grid when grouped by Date or Custom Tags
    val groupedFeedSections: StateFlow<List<GallerySection>> = combine(
        filteredPhotos,
        galleryGroupingMode,
        customAlbumTags
    ) { photos, mode, customTags ->
        when (mode) {
            GalleryGroupingMode.NONE -> emptyList()
            GalleryGroupingMode.DATE -> buildDateVirtualAlbums(photos).map { album ->
                GallerySection(
                    id = album.id,
                    title = album.title,
                    subtitle = "${album.photoCount} photo${if (album.photoCount == 1) "" else "s"}",
                    photos = album.photos
                )
            }
            GalleryGroupingMode.TAGS -> buildTagVirtualAlbums(photos, customTags)
                .filter { it.photos.isNotEmpty() }
                .map { album ->
                    GallerySection(
                        id = album.id,
                        title = if (album.isCustomTag) "#${album.title}" else album.title,
                        subtitle = "${album.photoCount} photo${if (album.photoCount == 1) "" else "s"}",
                        photos = album.photos
                    )
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun buildDateVirtualAlbums(photos: List<Photo>): List<VirtualAlbum> {
        if (photos.isEmpty()) return emptyList()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

        return photos
            .sortedByDescending { it.timestamp }
            .groupBy { photo -> keyFormat.format(Date(photo.timestamp)) }
            .entries
            .sortedByDescending { it.key }
            .map { (monthKey, groupPhotos) ->
                val title = monthFormat.format(Date(groupPhotos.first().timestamp))
                VirtualAlbum(
                    id = "date_$monthKey",
                    title = title,
                    subtitle = "${groupPhotos.size} item${if (groupPhotos.size == 1) "" else "s"} • Date Album",
                    coverUri = groupPhotos.first().uri,
                    photoCount = groupPhotos.size,
                    photos = groupPhotos,
                    groupingMode = AlbumGroupingMode.DATE,
                    tagKey = monthKey
                )
            }
    }

    private fun buildTagVirtualAlbums(photos: List<Photo>, customTags: List<String>): List<VirtualAlbum> {
        val customLowerSet = customTags.map { it.lowercase() }.toSet()
        val tagToDisplay = linkedMapOf<String, String>()
        val tagToPhotos = linkedMapOf<String, MutableList<Photo>>()

        // Seed user-created custom tag albums first (even if currently empty)
        for (custom in customTags) {
            val key = custom.lowercase()
            tagToDisplay[key] = custom
            tagToPhotos.getOrPut(key) { mutableListOf() }
        }

        // Group photos by each tag in Photo.tags
        for (photo in photos.sortedByDescending { it.timestamp }) {
            val photoTags = photo.tags
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }

            for (rawTag in photoTags) {
                val key = rawTag.lowercase()
                if (!tagToDisplay.containsKey(key)) {
                    tagToDisplay[key] = rawTag.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                }
                tagToPhotos.getOrPut(key) { mutableListOf() }.add(photo)
            }
        }

        return tagToPhotos.entries
            .map { (key, groupedPhotos) ->
                val displayTitle = tagToDisplay[key] ?: key
                val isCustom = key in customLowerSet
                VirtualAlbum(
                    id = "tag_$key",
                    title = displayTitle,
                    subtitle = buildString {
                        append("${groupedPhotos.size} item${if (groupedPhotos.size == 1) "" else "s"}")
                        append(if (isCustom) " • Custom Tag" else " • Auto Tag")
                    },
                    coverUri = groupedPhotos.firstOrNull()?.uri.orEmpty(),
                    photoCount = groupedPhotos.size,
                    photos = groupedPhotos,
                    groupingMode = AlbumGroupingMode.TAGS,
                    isCustomTag = isCustom,
                    tagKey = displayTitle
                )
            }
            .sortedWith(
                compareByDescending<VirtualAlbum> { it.isCustomTag }
                    .thenByDescending { it.photoCount }
                    .thenBy { it.title.lowercase() }
            )
    }

    init {
        // Seed initial photos if needed and load any already-permitted device MediaStore photos
        viewModelScope.launch {
            repository.seedMockPhotosIfEmpty()
            refreshDeviceMediaStore()
        }

        // React to connectivity transitions: pause on offline, trigger sync when reconnected
        viewModelScope.launch {
            var wasOffline = !isOnline.value
            isOnline.collect { online ->
                if (!online) {
                    wasOffline = true
                    repository.markOfflinePaused()
                } else if (wasOffline) {
                    wasOffline = false
                    triggerBackupSync(forceReverify = true)
                }
            }
        }
    }

    fun refreshDeviceMediaStore(isManualRefresh: Boolean = false) {
        viewModelScope.launch {
            val accessState = MediaStorePhotoLoader.checkMediaAccessState(getApplication())
            _mediaAccessState.value = accessState
            if (accessState != MediaAccessState.DENIED) {
                _isLoadingMediaStore.value = true
                try {
                    val inserted = repository.syncDeviceMediaStore()
                    if (isManualRefresh) {
                        delay(350)
                        val msg = if (inserted > 0) {
                            "Found $inserted new photo${if (inserted == 1) "" else "s"} in MediaStore"
                        } else {
                            "MediaStore scanned — gallery is up to date"
                        }
                        _mediaScanFeedback.tryEmit(msg)
                    }
                } finally {
                    _isLoadingMediaStore.value = false
                }
            }
        }
    }

    fun importPickedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isLoadingMediaStore.value = true
            try {
                repository.importPickedUris(uris)
            } finally {
                _isLoadingMediaStore.value = false
            }
        }
    }

    private var searchExpansionJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchExpansionJob?.cancel()
        if (query.isBlank()) {
            _expandedTags.value = emptyList()
            _isExpandingSearch.value = false
        } else {
            // Debounce background semantic tag expansion while filtering immediately
            searchExpansionJob = triggerSemanticSearchExpansion(query, debounceMs = 220L)
        }
    }

    fun updateSearchQuery(query: String) {
        onSearchQueryChanged(query)
    }

    fun submitSemanticSearch(query: String = _searchQuery.value) {
        _searchQuery.value = query
        searchExpansionJob?.cancel()
        if (query.isBlank()) {
            _expandedTags.value = emptyList()
            _isExpandingSearch.value = false
        } else {
            searchExpansionJob = triggerSemanticSearchExpansion(query, debounceMs = 0L)
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setGalleryGroupingMode(mode: GalleryGroupingMode) {
        _galleryGroupingMode.value = mode
    }

    fun selectVirtualAlbum(albumId: String?) {
        _selectedVirtualAlbumId.value = albumId
    }

    fun addTagToPhoto(photo: Photo, rawTag: String) {
        val cleanTag = rawTag.trim().replace(",", "")
        if (cleanTag.isEmpty()) return
        viewModelScope.launch {
            val currentTags = photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (currentTags.none { it.equals(cleanTag, ignoreCase = true) }) {
                currentTags.add(cleanTag)
                val updated = photo.copy(tags = currentTags.joinToString(", "))
                repository.updatePhoto(updated)
                if (_selectedPhoto.value?.id == photo.id) {
                    _selectedPhoto.value = updated
                }
            }
            val stored = virtualAlbumPrefs.getStringSet("custom_tags", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (stored.none { it.equals(cleanTag, ignoreCase = true) }) {
                stored.add(cleanTag)
                persistCustomTags(stored)
            }
        }
    }

    fun removeTagFromPhoto(photo: Photo, rawTag: String) {
        val cleanTag = rawTag.trim()
        if (cleanTag.isEmpty()) return
        viewModelScope.launch {
            val remaining = photo.tags
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(cleanTag, ignoreCase = true) }
            val updated = photo.copy(tags = remaining.joinToString(", "))
            repository.updatePhoto(updated)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = updated
            }
        }
    }

    fun createCustomTagAlbum(rawTag: String, selectedPhotoIds: Set<Long>) {
        val cleanTag = rawTag.trim().replace(",", "")
        if (cleanTag.isEmpty()) return
        viewModelScope.launch {
            val stored = virtualAlbumPrefs.getStringSet("custom_tags", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (stored.none { it.equals(cleanTag, ignoreCase = true) }) {
                stored.add(cleanTag)
                persistCustomTags(stored)
            }
            val currentPhotos = allPhotos.value
            for (photo in currentPhotos) {
                if (photo.id in selectedPhotoIds) {
                    val existingTags = photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    if (existingTags.none { it.equals(cleanTag, ignoreCase = true) }) {
                        existingTags.add(cleanTag)
                        repository.updatePhoto(photo.copy(tags = existingTags.joinToString(", ")))
                    }
                }
            }
        }
    }

    fun assignPhotosToTagAlbum(rawTag: String, selectedPhotoIds: Set<Long>) {
        createCustomTagAlbum(rawTag, selectedPhotoIds)
    }

    fun deleteCustomTagAlbum(rawTag: String) {
        val cleanTag = rawTag.trim()
        if (cleanTag.isEmpty()) return
        viewModelScope.launch {
            val stored = virtualAlbumPrefs.getStringSet("custom_tags", emptySet())?.toMutableSet() ?: mutableSetOf()
            stored.removeAll { it.equals(cleanTag, ignoreCase = true) }
            persistCustomTags(stored)

            for (photo in allPhotos.value) {
                val tags = photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tags.any { it.equals(cleanTag, ignoreCase = true) }) {
                    val filtered = tags.filterNot { it.equals(cleanTag, ignoreCase = true) }
                    val updated = photo.copy(tags = filtered.joinToString(", "))
                    repository.updatePhoto(updated)
                    if (_selectedPhoto.value?.id == photo.id) {
                        _selectedPhoto.value = updated
                    }
                }
            }
        }
    }

    fun selectPhoto(photo: Photo?) {
        if (photo == null) {
            _isSlideshowPlaying.value = false
        }
        _selectedPhoto.value = photo
    }

    fun startSlideshow() {
        val slides = slideshowPhotos.value
        if (slides.isEmpty()) {
            _isSlideshowPlaying.value = false
            return
        }
        val current = _selectedPhoto.value
        if (current == null || current.mediaType == com.example.data.model.MediaType.VIDEO) {
            _selectedPhoto.value = slides.first()
        }
        _slideshowDirection.value = 1
        _isSlideshowPlaying.value = slides.size > 1
    }

    fun pauseSlideshow() {
        _isSlideshowPlaying.value = false
    }

    fun stopSlideshow() {
        _isSlideshowPlaying.value = false
    }

    fun toggleSlideshow() {
        if (_isSlideshowPlaying.value) {
            pauseSlideshow()
        } else {
            startSlideshow()
        }
    }

    fun setSlideshowInterval(seconds: Int) {
        val clamped = seconds.coerceIn(2, 15)
        _slideshowIntervalSeconds.value = clamped
        slideshowPrefs.edit().putInt("interval_seconds", clamped).apply()
    }

    fun setSlideshowTransition(transition: SlideshowTransition) {
        _slideshowTransition.value = transition
        slideshowPrefs.edit().putString("transition_style", transition.name).apply()
    }

    fun setSlideshowLoopEnabled(enabled: Boolean) {
        _slideshowLoopEnabled.value = enabled
        slideshowPrefs.edit().putBoolean("loop_enabled", enabled).apply()
    }

    fun advanceSlideshowNext(manual: Boolean = false) {
        val slides = slideshowPhotos.value
        if (slides.isEmpty()) {
            _isSlideshowPlaying.value = false
            return
        }
        val currentId = _selectedPhoto.value?.id
        val currentIndex = slides.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex == -1) {
            0
        } else if (currentIndex >= slides.lastIndex) {
            if (!manual && !_slideshowLoopEnabled.value) {
                _isSlideshowPlaying.value = false
                return
            }
            0
        } else {
            currentIndex + 1
        }
        _slideshowDirection.value = 1
        _selectedPhoto.value = slides[nextIndex]
    }

    fun advanceSlideshowPrevious() {
        val slides = slideshowPhotos.value
        if (slides.isEmpty()) return
        val currentId = _selectedPhoto.value?.id
        val currentIndex = slides.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex <= 0) slides.lastIndex else currentIndex - 1
        _slideshowDirection.value = -1
        _selectedPhoto.value = slides[prevIndex]
    }

    fun saveEditedPhoto(
        photo: Photo,
        sourceBitmap: Bitmap,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect,
        saveAsCopy: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (_isSavingEdit.value) return
        viewModelScope.launch {
            _isSavingEdit.value = true
            val success = try {
                val transformed = withContext(Dispatchers.Default) {
                    ImageEditProcessor.applyCropAndRotate(
                        source = sourceBitmap,
                        rotationDegrees = rotationDegrees,
                        cropRect = cropRect
                    )
                }
                val saved = ImageEditProcessor.saveEditedBitmapToAppStorage(
                    context = getApplication(),
                    photoId = photo.id,
                    bitmap = transformed
                )

                if (saveAsCopy) {
                    val baseTitle = photo.title.removeSuffix(" (Edited)")
                    val copyPhoto = photo.copy(
                        id = 0,
                        uri = saved.uriString,
                        title = "$baseTitle (Edited)",
                        timestamp = System.currentTimeMillis(),
                        syncStatus = SyncStatus.LOCAL_ONLY,
                        cloudUrl = null,
                        storageSize = saved.byteSize
                    )
                    val newId = repository.addPhoto(copyPhoto)
                    val inserted = repository.getPhotoById(newId) ?: copyPhoto.copy(id = newId)
                    _selectedPhoto.value = inserted
                    _mediaScanFeedback.tryEmit("Saved edited copy")
                } else {
                    val updated = photo.copy(
                        uri = saved.uriString,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                        cloudUrl = null,
                        storageSize = saved.byteSize
                    )
                    repository.updatePhoto(updated)
                    if (_selectedPhoto.value?.id == photo.id) {
                        _selectedPhoto.value = updated
                    }
                    _mediaScanFeedback.tryEmit("Saved cropped & rotated photo")
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save edited photo: ${e.message}")
                _mediaScanFeedback.tryEmit("Failed to save edited photo")
                false
            } finally {
                _isSavingEdit.value = false
            }
            onComplete(success)
        }
    }

    fun toggleFavorite(photo: Photo) {
        viewModelScope.launch {
            val updated = photo.copy(isFavorite = !photo.isFavorite)
            repository.updatePhoto(updated)
            // If the selected photo is currently viewed, update its focus state
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = updated
            }
        }
    }

    fun addCustomPhoto(url: String) {
        addCustomMedia(url, com.example.data.model.MediaType.IMAGE)
    }

    fun addCustomMedia(url: String, mediaType: com.example.data.model.MediaType, durationMs: Long = 0L) {
        viewModelScope.launch {
            val isVideo = mediaType == com.example.data.model.MediaType.VIDEO
            val placeholderTitle = if (isVideo) "Cataloging Video..." else "Cataloging GIF..."
            val filePlaceholder = Photo(
                uri = url,
                title = placeholderTitle,
                description = "Lumina is cataloging your new media loop...",
                category = "Analyzing",
                syncStatus = SyncStatus.LOCAL_ONLY,
                mediaType = mediaType,
                duration = durationMs
            )
            val newId = repository.addPhoto(filePlaceholder)
            
            // Immediately run AI processing
            _isAnalyzing.value = true
            val parsedPhoto = repository.processPhotoWithAI(newId)
            if (parsedPhoto != null && _selectedPhoto.value?.id == newId) {
                _selectedPhoto.value = parsedPhoto
            }
            _isAnalyzing.value = false
        }
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            _selectedPhotoIds.update { it - photo.id }
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
        }
    }

    fun startMultiSelect(photoId: Long) {
        _selectedPhotoIds.update { it + photoId }
    }

    fun togglePhotoSelection(photoId: Long) {
        _selectedPhotoIds.update { current ->
            if (photoId in current) current - photoId else current + photoId
        }
    }

    fun selectAllPhotos(photos: List<Photo>) {
        val visibleIds = photos.map { it.id }.toSet()
        _selectedPhotoIds.update { current ->
            if (visibleIds.isNotEmpty() && visibleIds.all { it in current }) {
                emptySet()
            } else {
                current + visibleIds
            }
        }
    }

    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun deleteSelectedPhotos() {
        val idsToDelete = _selectedPhotoIds.value
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            val targets = allPhotos.value.filter { it.id in idsToDelete }
            targets.forEach { photo ->
                repository.deletePhoto(photo)
                if (_selectedPhoto.value?.id == photo.id) {
                    _selectedPhoto.value = null
                }
            }
            _selectedPhotoIds.value = emptySet()
            val count = targets.size
            _mediaScanFeedback.tryEmit("Deleted $count ${if (count == 1) "photo" else "photos"}")
        }
    }

    fun triggerManualAIReanalysis(photo: Photo) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            val parsedPhoto = repository.processPhotoWithAI(photo.id)
            if (parsedPhoto != null) {
                _selectedPhoto.value = parsedPhoto
            }
            _isAnalyzing.value = false
        }
    }

    fun triggerBackupSync(forceReverify: Boolean = false) {
        if (!isOnline.value) {
            _mediaScanFeedback.tryEmit("Offline — connect to Wi-Fi or mobile data to sync")
            return
        }
        viewModelScope.launch {
            repository.syncPhotosWithCloud(
                isOnlineProvider = { isOnline.value },
                forceReverify = forceReverify
            )
            // If active photo selected, refresh details
            val current = _selectedPhoto.value
            if (current != null) {
                val updated = repository.getPhotoById(current.id)
                if (updated != null) {
                    _selectedPhoto.value = updated
                }
            }
        }
    }

    private fun triggerSemanticSearchExpansion(query: String, debounceMs: Long = 0L): Job {
        return viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            _isExpandingSearch.value = true
            try {
                val tags = repository.expandSearchSemantically(query)
                if (_searchQuery.value == query) { // verify context hasn't changed
                    _expandedTags.value = tags
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed semantic expansion: ${e.message}")
            } finally {
                _isExpandingSearch.value = false
            }
        }
    }

    fun submitAdConsent(personalized: Boolean) {
        val state = if (personalized) {
            AdConsentState.PERSONALIZED
        } else {
            AdConsentState.NON_PERSONALIZED
        }
        adPreferencesRepository.setConsentState(state)
    }

    fun resetAdConsentPrompt() {
        adPreferencesRepository.setConsentState(AdConsentState.UNKNOWN)
    }

    fun setAdFreeEnabled(enabled: Boolean) {
        adPreferencesRepository.setAdFreeEnabled(enabled)
        _mediaScanFeedback.tryEmit(
            if (enabled) "Ad-Free mode enabled" else "Ads re-enabled via Unity LevelPlay"
        )
    }

    fun onRewardedStorageEarned() {
        val updatedBonus = adPreferencesRepository.grantStorageReward()
        val bonusGb = (updatedBonus / (1024L * 1024L * 1024L)).toInt()
        _mediaScanFeedback.tryEmit("Unlocked +5 GB cloud storage! (${bonusGb} GB bonus active)")
    }

    fun showAdFeedbackMessage(message: String) {
        _mediaScanFeedback.tryEmit(message)
    }

    fun recordPhotoExportAndCheckInterstitial(): Boolean {
        return adPreferencesRepository.recordExportAndCheckEligibility()
    }

    fun markExportInterstitialShown() {
        adPreferencesRepository.markInterstitialShown()
    }
}

data class AdMonetizationUiState(
    val consentState: AdConsentState = AdConsentState.UNKNOWN,
    val isAdFreeEnabled: Boolean = false,
    val bonusCloudStorageBytes: Long = 0L,
    val maxBonusCloudStorageBytes: Long = AdPolicyRules.MAX_BONUS_STORAGE_BYTES,
    val shouldShowAds: Boolean = false,
    val shouldPromptConsent: Boolean = true,
    val lastInterstitialShownAtMs: Long = 0L,
    val completedExportsCount: Int = 0
) {
    val canEarnMoreStorage: Boolean
        get() = bonusCloudStorageBytes < maxBonusCloudStorageBytes

    val bonusCloudGb: Int
        get() = (bonusCloudStorageBytes / (1024L * 1024L * 1024L)).toInt()
}

data class StorageStats(
    val usedBytesCloud: Long = 2L * 1024 * 1024 * 1024,
    val totalLimitCloud: Long = 15L * 1024 * 1024 * 1024,
    val bonusCloudBytes: Long = 0L,
    val localUnsyncedBytes: Long = 0,
    val localUnsyncedCount: Int = 0,
    val syncedCount: Int = 0,
    val totalPhotoCount: Int = 0,
    val totalLibraryBytes: Long = 0,
    val photosBytes: Long = 0,
    val photosCount: Int = 0,
    val videosBytes: Long = 0,
    val videosCount: Int = 0,
    val gifsAndDocsBytes: Long = 0,
    val gifsAndDocsCount: Int = 0,
    val syncedPhotosBytes: Long = 0,
    val syncedVideosBytes: Long = 0,
    val deviceTotalBytes: Long = 64L * 1024 * 1024 * 1024,
    val deviceAvailableBytes: Long = 32L * 1024 * 1024 * 1024,
    val percentUsed: Float = 0.13f
) {
    val deviceUsedBytes: Long
        get() = (deviceTotalBytes - deviceAvailableBytes).coerceAtLeast(0L)

    val devicePercentUsed: Float
        get() = if (deviceTotalBytes <= 0L) 0f else (deviceUsedBytes.toFloat() / deviceTotalBytes.toFloat()).coerceIn(0f, 1f)

    val cloudDocumentsBytes: Long
        get() = (usedBytesCloud - syncedPhotosBytes - syncedVideosBytes).coerceAtLeast(0L)

    val deviceDocumentsAndOtherBytes: Long
        get() = (deviceUsedBytes - photosBytes - videosBytes - gifsAndDocsBytes).coerceAtLeast(0L)

    fun formatStorageBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
            safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
            else -> "$safe B"
        }
    }

    fun getCloudUsageFormatted(): String {
        val gbUsed = usedBytesCloud.toDouble() / (1024 * 1024 * 1024)
        val gbTotal = totalLimitCloud.toDouble() / (1024 * 1024 * 1024)
        return String.format(Locale.US, "%.1f GB of %.0f GB used", gbUsed, gbTotal)
    }

    fun getLocalUnsyncedFormatted(): String {
        val mbUnsynced = localUnsyncedBytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB unsynced (%d items)", mbUnsynced, localUnsyncedCount)
    }
}

class PhotosViewModelFactory(
    private val application: Application,
    private val repository: PhotoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhotosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PhotosViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class SuggestedAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val searchQuery: String,
    val coverPhotoUrl: String,
    val type: String // THEME, LOCATION, DATE
)

enum class GalleryGroupingMode {
    NONE,
    DATE,
    TAGS
}

enum class AlbumGroupingMode {
    DATE,
    TAGS
}

data class VirtualAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUri: String,
    val photoCount: Int,
    val photos: List<Photo>,
    val groupingMode: AlbumGroupingMode,
    val isCustomTag: Boolean = false,
    val tagKey: String = ""
)

data class GallerySection(
    val id: String,
    val title: String,
    val subtitle: String,
    val photos: List<Photo>
)

enum class CloudSyncIndicatorState {
    SYNCING,
    SYNCED,
    OFFLINE
}

enum class SlideshowTransition(val label: String, val description: String) {
    CROSSFADE("Crossfade", "Smooth opacity blend between photos"),
    SLIDE("Slide", "Directional horizontal slide motion"),
    ZOOM("Ken Burns", "Gentle cinematic zoom & fade"),
    NONE("Instant", "Immediate cut with no animation")
}
