package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.ads.LevelPlayManager
import com.example.data.local.MediaAccessState
import com.example.data.local.MediaStorePhotoLoader
import com.example.data.model.Photo
import com.example.data.model.SyncStatus
import com.example.ui.components.BotanicalAccentCanvas
import com.example.ui.components.CloudSyncIndicatorButton
import com.example.ui.components.DashboardStorageSection
import com.example.ui.components.EmptyStateIllustrationView
import com.example.ui.components.NativeAdFeedCard
import com.example.ui.components.SyncProgressRing
import com.example.ui.components.TopAppBarSearchTextField
import com.example.ui.theme.LuminaLavenderBackdrop
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletLight
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.CloudSyncIndicatorState
import com.example.ui.viewmodel.GalleryGroupingMode
import com.example.ui.viewmodel.PhotosViewModel
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosFeedScreen(
    viewModel: PhotosViewModel,
    onPhotoSelected: (Photo) -> Unit,
    onNavigateToCloudSync: () -> Unit,
    onSearchClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { MediaStorePhotoLoader.findActivity(context) }

    val photos by viewModel.filteredPhotos.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isExpandingSearch by viewModel.isExpandingSearch.collectAsState()
    val expandedTags by viewModel.expandedTags.collectAsState()
    val storageStats by viewModel.storageInfo.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()
    val mediaAccessState by viewModel.mediaAccessState.collectAsState()
    val isLoadingMediaStore by viewModel.isLoadingMediaStore.collectAsState()
    val devicePhotoCount by viewModel.devicePhotoCount.collectAsState()
    val galleryGroupingMode by viewModel.galleryGroupingMode.collectAsState()
    val groupedFeedSections by viewModel.groupedFeedSections.collectAsState()
    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val adState by viewModel.adMonetizationState.collectAsState()
    val nativeAdLayout: NativeAdLayout? by LevelPlayManager.nativeAdLayout.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showProfileBackupPopup by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var isTopBarSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val topBarSearchFocusRequester = remember { FocusRequester() }
    var hasRequestedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var isPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isTopBarSearchExpanded) {
        if (isTopBarSearchExpanded) {
            topBarSearchFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isTopBarSearchExpanded || searchQuery.isNotBlank()) {
        viewModel.onSearchQueryChanged("")
        isTopBarSearchExpanded = false
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(viewModel) {
        viewModel.mediaScanFeedback.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val updatedState = MediaStorePhotoLoader.checkMediaAccessState(context)
        if (updatedState == MediaAccessState.DENIED) {
            val canShowRationale = activity?.let { MediaStorePhotoLoader.shouldShowRationale(it) } ?: false
            isPermanentlyDenied = !canShowRationale
        } else {
            isPermanentlyDenied = false
        }
        viewModel.refreshDeviceMediaStore()
    }

    val requestMediaPermissionOrRationale: () -> Unit = {
        when {
            isPermanentlyDenied -> {
                MediaStorePhotoLoader.openAppSettings(context)
            }
            activity != null && MediaStorePhotoLoader.shouldShowRationale(activity) -> {
                showPermissionRationale = true
            }
            else -> {
                mediaPermissionLauncher.launch(MediaStorePhotoLoader.requiredPermissions())
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedPhotos(uris)
        }
    }

    // Refresh MediaStore state when returning from system Settings or permission prompt
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latestState = MediaStorePhotoLoader.checkMediaAccessState(context)
                if (latestState != MediaAccessState.DENIED) {
                    isPermanentlyDenied = false
                }
                viewModel.refreshDeviceMediaStore()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val currentState = MediaStorePhotoLoader.checkMediaAccessState(context)
        if (currentState == MediaAccessState.DENIED && !hasRequestedPermissionOnce) {
            hasRequestedPermissionOnce = true
            if (activity != null && MediaStorePhotoLoader.shouldShowRationale(activity)) {
                showPermissionRationale = true
            } else {
                mediaPermissionLauncher.launch(MediaStorePhotoLoader.requiredPermissions())
            }
        } else {
            viewModel.refreshDeviceMediaStore()
        }
    }

    val categories = listOf("All", "Device", "Nature", "People", "Food", "Places", "Things", "Other")

    val allPhotos by viewModel.allPhotos.collectAsState()
    val syncedCount = remember(allPhotos) {
        allPhotos.count { it.syncStatus == SyncStatus.SYNCED }
    }
    val syncedFraction = remember(allPhotos, syncedCount) {
        if (allPhotos.isEmpty()) 0f else syncedCount.toFloat() / allPhotos.size.toFloat()
    }
    val syncedPercent = (syncedFraction * 100).toInt()

    val isSearchModeActive = isTopBarSearchExpanded || searchQuery.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LuminaLavenderBackdrop,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    modifier = Modifier.testTag("contextual_action_bar"),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LuminaVioletPrimary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("selection_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_exit_selection),
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedPhotoIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("selection_count_text")
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectAllPhotos(photos) },
                            modifier = Modifier.testTag("selection_select_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.action_select_all),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                val selectedPhotos = allPhotos.filter { it.id in selectedPhotoIds }
                                shareSelectedPhotos(context, selectedPhotos)
                                if (selectedPhotos.isNotEmpty() &&
                                    viewModel.recordPhotoExportAndCheckInterstitial() &&
                                    activity != null
                                ) {
                                    LevelPlayManager.showInterstitialIfReady(activity) {
                                        viewModel.markExportInterstitialShown()
                                    }
                                }
                            },
                            enabled = selectedPhotoIds.isNotEmpty(),
                            modifier = Modifier.testTag("contextual_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share_selected),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedPhotoIds.isNotEmpty(),
                            modifier = Modifier.testTag("contextual_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete_selected),
                                tint = Color.White
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LuminaLavenderBackdrop
                    ),
                    navigationIcon = {
                        if (isSearchModeActive) {
                            IconButton(
                                onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    isTopBarSearchExpanded = false
                                },
                                modifier = Modifier.testTag("topbar_search_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_close_search),
                                    tint = LuminaOnSurface
                                )
                            }
                        }
                    },
                    title = {
                        if (isSearchModeActive) {
                            TopAppBarSearchTextField(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                onSearchSubmit = { viewModel.submitSemanticSearch() },
                                isSearching = isExpandingSearch,
                                focusRequester = topBarSearchFocusRequester,
                                testTagName = "topbar_search_textfield"
                            )
                        } else {
                            Column {
                                Text(
                                    text = "Welcome back,",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LuminaOnSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Lumina Gallery",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LuminaOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isSearchModeActive) {
                            IconButton(
                                onClick = { isTopBarSearchExpanded = true },
                                modifier = Modifier.testTag("topbar_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.action_open_search),
                                    tint = LuminaOnSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (mediaAccessState == MediaAccessState.DENIED) {
                                    requestMediaPermissionOrRationale()
                                } else {
                                    viewModel.refreshDeviceMediaStore(isManualRefresh = true)
                                }
                            },
                            modifier = Modifier.testTag("refresh_mediastore_button")
                        ) {
                            if (isLoadingMediaStore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LuminaVioletPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.action_refresh_gallery),
                                    tint = LuminaOnSurface
                                )
                            }
                        }

                        CloudSyncIndicatorButton(
                            syncState = cloudSyncState,
                            onClick = {
                                when (cloudSyncState) {
                                    CloudSyncIndicatorState.SYNCED -> viewModel.triggerBackupSync(forceReverify = true)
                                    CloudSyncIndicatorState.SYNCING -> showProfileBackupPopup = true
                                    CloudSyncIndicatorState.OFFLINE -> viewModel.triggerBackupSync()
                                }
                            },
                            showLabel = !isSearchModeActive
                        )

                        if (!isSearchModeActive) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(LuminaVioletPrimary)
                                    .clickable { showProfileBackupPopup = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "DS",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = LuminaSkyAccent,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("add_photo_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Photo",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LuminaLavenderBackdrop,
                            LuminaLavenderBackdrop.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // --- Soft Pastel Lavender Hero Zone (Inspired by Left Reference Screen) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                BotanicalAccentCanvas(
                    lineColor = LuminaVioletPrimary.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(84.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-4).dp)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pure White Pill Search Bar (opens Discover or focuses TopAppBar semantic search)
                    if (!isSearchModeActive) {
                        Surface(
                            onClick = { isTopBarSearchExpanded = true },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("feed_quick_search_pill")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = LuminaOnSurface.copy(alpha = 0.65f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Search \"mountain sunset\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LuminaOnSurface.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = onSearchClicked,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Discover",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LuminaVioletPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Vibrant Violet Hero Card with Sync Ring (Inspired by Left Reference Screen)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToCloudSync() },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(LuminaVioletPrimary, LuminaVioletLight)
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            BotanicalAccentCanvas(
                                lineColor = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .size(72.dp)
                                    .align(Alignment.CenterEnd)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SyncProgressRing(
                                    progress = syncedFraction,
                                    centerLabel = "$syncedPercent%",
                                    trackColor = Color.White.copy(alpha = 0.24f),
                                    progressColor = Color.White,
                                    textColor = Color.White,
                                    size = 52.dp,
                                    strokeWidth = 4.5.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Best of Summer · 1 year ago",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${allPhotos.size} memories • $devicePhotoCount on device",
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Overlapping Pure White Rounded Bottom Sheet (Inspired by Reference Screens) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Subtle Sheet Grab Pill
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 4.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterHorizontally)
                    )

                    // Live Semantic Visual Search Expansion Chips
                    AnimatedVisibility(
                        visible = searchQuery.isNotBlank() && (isExpandingSearch || expandedTags.isNotEmpty()),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = LuminaVioletPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isExpandingSearch) "Expanding visual concepts..." else "Semantic concepts",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LuminaVioletPrimary
                                )
                            }
                            if (expandedTags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(expandedTags) { tag ->
                                        AssistChip(
                                            onClick = { viewModel.onSearchQueryChanged(tag) },
                                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Horizontal Category Filter List
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(category) },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        val icon = when (category) {
                                            "Device" -> Icons.Default.Smartphone
                                            "Nature" -> Icons.Default.Landscape
                                            "People" -> Icons.Default.Face
                                            "Food" -> Icons.Default.Restaurant
                                            "Places" -> Icons.Default.Place
                                            "Things" -> Icons.Default.Category
                                            "Other" -> Icons.Default.BubbleChart
                                            else -> Icons.Default.PhotoLibrary
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("category_chip_$category")
                            )
                        }
                    }

                    // Device MediaStore permission / status banner
                    if (mediaAccessState != MediaAccessState.GRANTED_ALL) {
                        DeviceMediaAccessBanner(
                            accessState = mediaAccessState,
                            isPermanentlyDenied = isPermanentlyDenied,
                            devicePhotoCount = devicePhotoCount,
                            onRequestPermission = requestMediaPermissionOrRationale,
                            onOpenPhotoPicker = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }

                    // Virtual Album Grouping Strip (Flat vs Date vs Custom Tags)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Group:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        listOf(
                            Triple(GalleryGroupingMode.NONE, R.string.group_flat, Icons.Default.GridView),
                            Triple(GalleryGroupingMode.DATE, R.string.group_by_date, Icons.Default.CalendarMonth),
                            Triple(GalleryGroupingMode.TAGS, R.string.group_by_tags, Icons.Default.Tag)
                        ).forEach { (mode, labelRes, icon) ->
                            FilterChip(
                                selected = galleryGroupingMode == mode,
                                onClick = { viewModel.setGalleryGroupingMode(mode) },
                                label = { Text(stringResource(labelRes), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("gallery_group_${mode.name.lowercase()}")
                            )
                        }
                    }

                    val pullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isLoadingMediaStore,
                        onRefresh = {
                            if (mediaAccessState == MediaAccessState.DENIED) {
                                requestMediaPermissionOrRationale()
                            } else {
                                viewModel.refreshDeviceMediaStore(isManualRefresh = true)
                            }
                        },
                        state = pullToRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isLoadingMediaStore,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                color = LuminaSkyAccent,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .testTag("pull_refresh_indicator")
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("photo_gallery_pull_refresh")
                    ) {
                        if (photos.isEmpty()) {
                            // AI-generated Empty Gallery Illustration + Message
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (searchQuery.isBlank() && !isSelectionMode) {
                                    DashboardStorageSection(
                                        storageStats = storageStats,
                                        onManageStorageClick = onNavigateToCloudSync,
                                        onQuickSyncClick = { viewModel.triggerBackupSync(forceReverify = true) }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isSearchEmpty = searchQuery.isNotBlank()
                                    val emptyDrawable = if (isSearchEmpty) R.drawable.img_empty_search else R.drawable.img_empty_gallery
                                    val emptyTitle = when {
                                        isSearchEmpty -> "No Visual Matches"
                                        selectedCategory == "Device" -> "No Device Photos Loaded"
                                        else -> "Your Gallery Awaits"
                                    }
                                    val emptyMessage = when {
                                        isSearchEmpty ->
                                            "No memories matched \"$searchQuery\". Try another visual mood, place, or clear the search."
                                        selectedCategory == "Device" && mediaAccessState == MediaAccessState.DENIED ->
                                            "Grant MediaStore permission or use the Photo Picker to display local photos here."
                                        selectedCategory == "Device" ->
                                            "No local photos were found in your device MediaStore yet. Pull down to scan or pick photos."
                                        selectedCategory != "All" ->
                                            "No photos are cataloged under \"$selectedCategory\" yet. Try another filter or import new memories."
                                        else ->
                                            "Your Lumina gallery has no photos yet. Import local photos from your device or add a memory to begin."
                                    }
                                    val emptyActionLabel = when {
                                        isSearchEmpty -> "Clear Search"
                                        selectedCategory == "Device" && mediaAccessState == MediaAccessState.DENIED ->
                                            stringResource(R.string.action_allow_access)
                                        selectedCategory != "All" ->
                                            "Show All Photos"
                                        else ->
                                            "Import Local Photos"
                                    }
                                    EmptyStateIllustrationView(
                                        drawableRes = emptyDrawable,
                                        imageContentDescription = if (isSearchEmpty) "Empty search results illustration" else "Empty photo gallery illustration",
                                        title = emptyTitle,
                                        message = emptyMessage,
                                        actionLabel = emptyActionLabel,
                                        onActionClick = {
                                            when {
                                                isSearchEmpty -> {
                                                    viewModel.onSearchQueryChanged("")
                                                    isTopBarSearchExpanded = false
                                                }
                                                selectedCategory == "Device" && mediaAccessState == MediaAccessState.DENIED ->
                                                    requestMediaPermissionOrRationale()
                                                selectedCategory != "All" ->
                                                    viewModel.selectCategory("All")
                                                else ->
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                            }
                                        },
                                        testTagPrefix = if (isSearchEmpty) "empty_feed_search" else "empty_gallery"
                                    )
                                }
                            }
                        } else {
                    // High-density, responsive LazyVerticalGrid (flat or grouped into virtual albums)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp)
                            .testTag("photo_gallery_grid"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 2.dp)
                    ) {
                        if (searchQuery.isBlank() && !isSelectionMode) {
                            item(
                                key = "dashboard_storage_cards",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                DashboardStorageSection(
                                    storageStats = storageStats,
                                    onManageStorageClick = onNavigateToCloudSync,
                                    onQuickSyncClick = { viewModel.triggerBackupSync(forceReverify = true) }
                                )
                            }
                        }

                        val showFeedNativeAd = adState.shouldShowAds &&
                            nativeAdLayout != null &&
                            searchQuery.isBlank() &&
                            !isSelectionMode

                        if (galleryGroupingMode == GalleryGroupingMode.NONE) {
                            val chunks = photos.chunked(12)
                            chunks.forEachIndexed { chunkIndex, chunk ->
                                items(chunk, key = { it.id }) { photo ->
                                    val isSelected = photo.id in selectedPhotoIds
                                    PhotoGridTile(
                                        photo = photo,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onPhotoSelected = { tapped ->
                                            if (isSelectionMode) {
                                                viewModel.togglePhotoSelection(tapped.id)
                                            } else {
                                                onPhotoSelected(tapped)
                                            }
                                        },
                                        onFavoriteToggled = { viewModel.toggleFavorite(photo) },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                viewModel.togglePhotoSelection(photo.id)
                                            } else {
                                                viewModel.startMultiSelect(photo.id)
                                            }
                                        }
                                    )
                                }
                                if (showFeedNativeAd && chunkIndex == 0 && photos.size >= 4) {
                                    item(
                                        key = "native_ad_feed_slot_$chunkIndex",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        NativeAdFeedCard(nativeAdLayout = nativeAdLayout)
                                    }
                                }
                            }
                        } else {
                            groupedFeedSections.forEachIndexed { sectionIndex, section ->
                                item(
                                    key = "header_${section.id}",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = section.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                items(
                                    items = section.photos,
                                    key = { photo -> "${section.id}_${photo.id}" }
                                ) { photo ->
                                    val isSelected = photo.id in selectedPhotoIds
                                    PhotoGridTile(
                                        photo = photo,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onPhotoSelected = { tapped ->
                                            if (isSelectionMode) {
                                                viewModel.togglePhotoSelection(tapped.id)
                                            } else {
                                                onPhotoSelected(tapped)
                                            }
                                        },
                                        onFavoriteToggled = { viewModel.toggleFavorite(photo) },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                viewModel.togglePhotoSelection(photo.id)
                                            } else {
                                                viewModel.startMultiSelect(photo.id)
                                            }
                                        }
                                    )
                                }
                                if (showFeedNativeAd && sectionIndex == 0) {
                                    item(
                                        key = "native_ad_section_slot_$sectionIndex",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        NativeAdFeedCard(nativeAdLayout = nativeAdLayout)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

    // Modern profile backup popup
    if (showProfileBackupPopup) {
        Dialog(onDismissRequest = { showProfileBackupPopup = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Star",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lumina Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Backup status detail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBackupDone = storageStats.localUnsyncedCount == 0
                        Icon(
                            imageVector = when (cloudSyncState) {
                                CloudSyncIndicatorState.OFFLINE -> Icons.Default.CloudOff
                                CloudSyncIndicatorState.SYNCING -> Icons.Default.Sync
                                CloudSyncIndicatorState.SYNCED -> if (isBackupDone) Icons.Default.CloudDone else Icons.Default.CloudQueue
                            },
                            contentDescription = null,
                            tint = when (cloudSyncState) {
                                CloudSyncIndicatorState.OFFLINE -> Color(0xFFC62828)
                                CloudSyncIndicatorState.SYNCING -> MaterialTheme.colorScheme.primary
                                CloudSyncIndicatorState.SYNCED -> if (isBackupDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (cloudSyncState) {
                                    CloudSyncIndicatorState.OFFLINE -> "Device is offline"
                                    CloudSyncIndicatorState.SYNCING -> "Syncing library..."
                                    CloudSyncIndicatorState.SYNCED -> if (isBackupDone) "Backup complete" else "Online • Ready to sync"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (cloudSyncState) {
                                    CloudSyncIndicatorState.OFFLINE -> "Connect to Wi-Fi or mobile data to resume cloud sync"
                                    CloudSyncIndicatorState.SYNCING -> "Uploading files..."
                                    CloudSyncIndicatorState.SYNCED -> if (isBackupDone) "All photos are safe in the cloud" else "${storageStats.localUnsyncedCount} items wait to sync"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Storage Indicator
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Lumina Cloud Storage",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = storageStats.getCloudUsageFormatted(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { storageStats.percentUsed },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ad-Free & Privacy Preferences Quick Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ad_free_title),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.ad_free_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = adState.isAdFreeEnabled,
                            onCheckedChange = { viewModel.setAdFreeEnabled(it) },
                            modifier = Modifier.testTag("profile_ad_free_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showProfileBackupPopup = false }) {
                            Text("Close")
                        }
                        Button(
                            onClick = {
                                showProfileBackupPopup = false
                                onNavigateToCloudSync()
                            }
                        ) {
                            Text("Manage Cloud")
                        }
                    }
                }
            }
        }
    }

    // Modern media-adding dialog
    if (showAddDialog) {
        var customUrl by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(com.example.data.model.MediaType.IMAGE) }
        var customDuration by remember { mutableStateOf("12") }

        val imagePresets = listOf(
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800" to "Blooming Valley",
            "https://images.unsplash.com/photo-1549488344-1f9b8d2bd1f3?w=800" to "Muted Wilderness",
            "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=800" to "Portrait Shimmer",
            "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800" to "Gourmet Platter"
        )

        val videoPresets = listOf(
            "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4" to "Cinematic Forest Stream",
            "https://assets.mixkit.co/videos/preview/mixkit-waves-breaking-on-the-pEB-large.mp4" to "Waves Crushing Sandy Coast"
        )

        val gifPresets = listOf(
            "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHIyb2h5bm1hZnlwMjFocXh0Mm14MWhybWoxN2NlZTR3NXNqczd0cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/MDJ9IbxxvDUQM/giphy.gif" to "Cheerful Climbing Kitten",
            "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOXVrdTRmYTFjdmt5NXhycXhuZDRoYXp4bTZuYTNrcGRqNGw1YzFqNyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3d6WO0F9sk9h9e9dfc/giphy.gif" to "Vaporwave Horizon Horizon"
        )

        val recommendations = when (selectedType) {
            com.example.data.model.MediaType.IMAGE -> imagePresets
            com.example.data.model.MediaType.VIDEO -> videoPresets
            com.example.data.model.MediaType.GIF -> gifPresets
        }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add Media Memory",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import local photos from your device or specify a web URL for Lumina AI cataloging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            showAddDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_pick_local_photos_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Local Photos from Device")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Type Selection Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            com.example.data.model.MediaType.IMAGE to "Photo",
                            com.example.data.model.MediaType.VIDEO to "Video",
                            com.example.data.model.MediaType.GIF to "GIF"
                        ).forEach { (type, label) ->
                            val isSelected = selectedType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedType = type
                                    customUrl = "" // reset link on type switch
                                },
                                label = { Text(label) },
                                leadingIcon = {
                                    val icon = when (type) {
                                        com.example.data.model.MediaType.IMAGE -> Icons.Default.InsertPhoto
                                        com.example.data.model.MediaType.VIDEO -> Icons.Default.Videocam
                                        com.example.data.model.MediaType.GIF -> Icons.Default.Gif
                                    }
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_photo_url_input"),
                        label = { Text(if (selectedType == com.example.data.model.MediaType.IMAGE) "Image resource URL" else if (selectedType == com.example.data.model.MediaType.VIDEO) "Video (MP4) stream URL" else "GIF resource URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (selectedType == com.example.data.model.MediaType.VIDEO) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customDuration,
                            onValueChange = { customDuration = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Video Duration (seconds)") },
                            placeholder = { Text("12") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recommendations.forEach { (url, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { customUrl = url }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val leadingIcon = when (selectedType) {
                                    com.example.data.model.MediaType.IMAGE -> Icons.Default.AddPhotoAlternate
                                    com.example.data.model.MediaType.VIDEO -> Icons.Default.VideoCall
                                    com.example.data.model.MediaType.GIF -> Icons.Default.AddReaction
                                }
                                Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customUrl.isNotBlank()) {
                                    if (selectedType == com.example.data.model.MediaType.IMAGE) {
                                        viewModel.addCustomPhoto(customUrl)
                                    } else {
                                        val durSecs = customDuration.toLongOrNull() ?: 12L
                                        viewModel.addCustomMedia(customUrl, selectedType, durSecs * 1000)
                                    }
                                    showAddDialog = false
                                }
                            },
                            enabled = customUrl.isNotBlank(),
                            modifier = Modifier.testTag("commit_add_photo_button")
                        ) {
                            Text("Catalog with AI")
                        }
                    }
                }
            }
        }
    }
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PermMedia,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.permission_rationale_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.permission_rationale_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        mediaPermissionLauncher.launch(MediaStorePhotoLoader.requiredPermissions())
                    },
                    modifier = Modifier.testTag("rationale_confirm_button")
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionRationale = false },
                    modifier = Modifier.testTag("rationale_dismiss_button")
                ) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
            modifier = Modifier.testTag("media_permission_rationale_dialog")
        )
    }
    if (showDeleteSelectedDialog) {
        val count = selectedPhotoIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_selected_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove $count selected ${if (count == 1) "photo" else "photos"} from your Lumina gallery?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectedDialog = false
                        viewModel.deleteSelectedPhotos()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_selected_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteSelectedDialog = false },
                    modifier = Modifier.testTag("cancel_delete_selected_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag("delete_selected_confirmation_dialog")
        )
    }
}

private fun shareSelectedPhotos(context: Context, photos: List<Photo>) {
    if (photos.isEmpty()) return

    val localUris = photos.mapNotNull { photo ->
        if (photo.uri.startsWith("content://")) Uri.parse(photo.uri) else null
    }
    val remotePhotos = photos.filter { !it.uri.startsWith("content://") }

    val shareIntent = if (localUris.isNotEmpty() && remotePhotos.isEmpty()) {
        val hasVideo = photos.any { it.mediaType == com.example.data.model.MediaType.VIDEO }
        val mimeType = if (hasVideo) "*/*" else "image/*"
        if (localUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, localUris.first())
                putExtra(Intent.EXTRA_TEXT, photos.first().title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(localUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    } else {
        val summary = photos.joinToString(separator = "\n") { "${it.title}: ${it.uri}" }
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shared from Lumina (${photos.size} memories)")
            putExtra(Intent.EXTRA_TEXT, summary)
            if (localUris.isNotEmpty()) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(localUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    val chooser = Intent.createChooser(
        shareIntent,
        "Share ${photos.size} ${if (photos.size == 1) "photo" else "photos"}"
    )
    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        // Gracefully ignore if no target app is available on minimal emulator builds
    }
}

@Composable
fun DeviceMediaAccessBanner(
    accessState: MediaAccessState,
    isPermanentlyDenied: Boolean,
    devicePhotoCount: Int,
    onRequestPermission: () -> Unit,
    onOpenPhotoPicker: () -> Unit
) {
    val isPartial = accessState == MediaAccessState.GRANTED_PARTIAL
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("device_media_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PermMedia,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPartial) {
                        "${stringResource(R.string.device_photos_partial_title)} ($devicePhotoCount)"
                    } else {
                        stringResource(R.string.device_photos_permission_title)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    isPartial -> stringResource(R.string.device_photos_partial_desc)
                    isPermanentlyDenied -> stringResource(R.string.device_photos_permanently_denied_desc)
                    else -> stringResource(R.string.device_photos_permission_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRequestPermission,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("grant_media_permission_button")
                ) {
                    Text(
                        text = when {
                            isPartial -> stringResource(R.string.action_select_more)
                            isPermanentlyDenied -> stringResource(R.string.action_open_settings)
                            else -> stringResource(R.string.action_allow_access)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = onOpenPhotoPicker,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pick_device_photos_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_pick_photos),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridTile(
    photo: Photo,
    onPhotoSelected: (Photo) -> Unit,
    onFavoriteToggled: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isLocalDevicePhoto = photo.uri.startsWith("content://")
    val innerPadding by animateDpAsState(
        targetValue = if (isSelected) 5.dp else 0.dp,
        label = "selection_tile_inset"
    )
    val tileShape = if (isSelected) RoundedCornerShape(10.dp) else RoundedCornerShape(2.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(innerPadding)
            .clip(tileShape)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, LuminaSkyAccent, tileShape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = { onPhotoSelected(photo) },
                onLongClick = { (onLongPress ?: onFavoriteToggled).invoke() }
            )
            .testTag("photo_tile_${photo.id}"),
        shape = tileShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = remember(photo.uri) {
                    ImageRequest.Builder(context)
                        .data(photo.uri)
                        .crossfade(true)
                        .build()
                },
                contentDescription = photo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Multi-select overlay scrim & circular checkbox badge
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                LuminaVioletPrimary.copy(alpha = 0.24f)
                            } else {
                                Color.Black.copy(alpha = 0.14f)
                            }
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) LuminaSkyAccent else Color.Black.copy(alpha = 0.35f)
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .testTag(
                            if (isSelected) "photo_selected_badge_${photo.id}" else "photo_unselected_badge_${photo.id}"
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Top right status indicators: Cloud state + Fav
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (photo.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorited",
                            tint = Color(0xFFE91E63),
                            modifier = Modifier
                                .size(18.dp)
                                .shadowBackgroundHelper()
                        )
                    }

                    if (isLocalDevicePhoto) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Device MediaStore Photo",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .shadowBackgroundHelper()
                        )
                    }

                    if (photo.syncStatus != SyncStatus.SYNCED) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Local Only",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .shadowBackgroundHelper()
                        )
                    }
                }
            }

            // Overlay play indicators centered for videos, or GIF indicators top-left
            if (photo.mediaType == com.example.data.model.MediaType.VIDEO) {
                // Central translucent play button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Small duration label at top start (offset when selection badge is visible)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = if (isSelectionMode) 34.dp else 6.dp,
                            top = 6.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    val totalSecs = photo.duration / 1000
                    Text(
                        text = "${totalSecs}s",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (photo.mediaType == com.example.data.model.MediaType.GIF) {
                // Animated GIF badge (Top left, offset when selection badge is visible)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = if (isSelectionMode) 34.dp else 6.dp,
                            top = 6.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "GIF",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Bottom dynamic visual label gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(vertical = 8.dp, horizontal = 6.dp)
            ) {
                Text(
                    text = photo.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Visual shadow booster modifier helper to make white icons readable under any graphic backdrops
@Composable
fun Modifier.shadowBackgroundHelper(): Modifier {
    return this
        .background(Color.Black.copy(alpha = 0.35f), shape = CircleShape)
        .padding(2.dp)
}
