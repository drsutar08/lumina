package com.example.ui.screens

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ads.LevelPlayManager
import com.example.data.local.MediaStorePhotoLoader
import com.example.data.model.Photo
import com.example.data.model.SyncStatus
import com.example.ui.components.BotanicalAccentCanvas
import com.example.ui.components.CloudSyncIndicatorButton
import com.example.ui.components.PhotoEditorDialog
import com.example.ui.components.SlideshowPlayerBar
import com.example.ui.components.SlideshowSettingsDialog
import com.example.ui.components.slideshowTransitionSpec
import com.example.ui.theme.LuminaInfoStripBlue
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletLight
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.PhotosViewModel
import com.example.ui.viewmodel.SlideshowTransition
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailsScreen(
    viewModel: PhotosViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val activity = remember(context) { MediaStorePhotoLoader.findActivity(context) }
    val photoState by viewModel.selectedPhoto.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val customAlbumTags by viewModel.customAlbumTags.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()
    val isSlideshowPlaying by viewModel.isSlideshowPlaying.collectAsState()
    val slideshowIntervalSeconds by viewModel.slideshowIntervalSeconds.collectAsState()
    val slideshowTransition by viewModel.slideshowTransition.collectAsState()
    val slideshowLoopEnabled by viewModel.slideshowLoopEnabled.collectAsState()
    val slideshowDirection by viewModel.slideshowDirection.collectAsState()
    val slideshowPhotos by viewModel.slideshowPhotos.collectAsState()
    val isSavingEdit by viewModel.isSavingEdit.collectAsState()
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showFullscreenViewer by remember { mutableStateOf(false) }
    var showSlideshowSettings by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    val slideshowProgress = remember { Animatable(0f) }

    val photo = photoState ?: return

    val currentSlideIndex = remember(slideshowPhotos, photo.id) {
        slideshowPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
    }

    LaunchedEffect(
        isSlideshowPlaying,
        photo.id,
        slideshowIntervalSeconds,
        slideshowPhotos.size,
        showSlideshowSettings
    ) {
        if (!isSlideshowPlaying || showSlideshowSettings || slideshowPhotos.size <= 1) {
            slideshowProgress.snapTo(0f)
            return@LaunchedEffect
        }
        slideshowProgress.snapTo(0f)
        slideshowProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = slideshowIntervalSeconds * 1000,
                easing = LinearEasing
            )
        )
        if (isSlideshowPlaying && !showSlideshowSettings) {
            viewModel.advanceSlideshowNext(manual = false)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LuminaVioletPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = photo.title,
                        maxLines = 1,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    CloudSyncIndicatorButton(
                        syncState = cloudSyncState,
                        onClick = { viewModel.triggerBackupSync(forceReverify = true) },
                        showLabel = false,
                        onDarkSurface = true
                    )

                    if (photo.mediaType == com.example.data.model.MediaType.IMAGE) {
                        IconButton(
                            onClick = {
                                viewModel.pauseSlideshow()
                                showPhotoEditor = true
                            },
                            modifier = Modifier.testTag("detail_edit_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropRotate,
                                contentDescription = stringResource(R.string.action_edit_photo),
                                tint = Color.White
                            )
                        }
                    }

                    if (slideshowPhotos.size > 1) {
                        IconButton(
                            onClick = { viewModel.toggleSlideshow() },
                            modifier = Modifier.testTag("topbar_slideshow_button")
                        ) {
                            Icon(
                                imageVector = if (isSlideshowPlaying) {
                                    Icons.Default.PauseCircle
                                } else {
                                    Icons.Default.Slideshow
                                },
                                contentDescription = if (isSlideshowPlaying) {
                                    stringResource(R.string.action_pause_slideshow)
                                } else {
                                    stringResource(R.string.action_start_slideshow)
                                },
                                tint = Color.White
                            )
                        }
                    }

                    // Gemini AI Analysis trigger button
                    IconButton(
                        onClick = { viewModel.triggerManualAIReanalysis(photo) },
                        enabled = !isAnalyzing,
                        modifier = Modifier.testTag("gemini_reanalyze_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Run Gemini analysis",
                                tint = Color.White
                            )
                        }
                    }

                    // Favorite Toggle
                    IconButton(
                        onClick = { viewModel.toggleFavorite(photo) },
                        modifier = Modifier.testTag("favorite_button")
                    ) {
                        Icon(
                            imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite toggle",
                            tint = if (photo.isFavorite) Color(0xFFFF80AB) else Color.White
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = {
                            viewModel.deletePhoto(photo)
                            onBack()
                        },
                        modifier = Modifier.testTag("delete_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete photo",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuminaVioletPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(LuminaVioletPrimary, LuminaVioletLight)
                    )
                )
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Media render frame inside Violet Top Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BotanicalAccentCanvas(
                    lineColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(276.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = photo,
                        transitionSpec = {
                            slideshowTransitionSpec(slideshowTransition, slideshowDirection)
                        },
                        contentKey = { it.id },
                        modifier = Modifier.fillMaxSize(),
                        label = "HeroSlideshowTransition"
                    ) { slidePhoto ->
                        val kenBurnsScale = if (isSlideshowPlaying && slideshowTransition == SlideshowTransition.ZOOM) {
                            1f + (slideshowProgress.value * 0.06f)
                        } else {
                            1f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = kenBurnsScale
                                    scaleY = kenBurnsScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (slidePhoto.mediaType == com.example.data.model.MediaType.VIDEO) {
                                val videoStreamUrl = if (!slidePhoto.cloudUrl.isNullOrBlank() && slidePhoto.cloudUrl.endsWith(".mp4")) {
                                    slidePhoto.cloudUrl
                                } else {
                                    slidePhoto.uri
                                }
                                VideoPlayer(
                                    videoUrl = videoStreamUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ZoomablePhotoView(
                                    imageUri = slidePhoto.uri,
                                    contentDescription = slidePhoto.title,
                                    resetKey = slidePhoto.id,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("detail_zoomable_image")
                                )
                            }
                        }
                    }

                    if (photo.mediaType != com.example.data.model.MediaType.VIDEO) {
                        // Fullscreen inspection trigger button (Top-Right)
                        IconButton(
                            onClick = { showFullscreenViewer = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .testTag("detail_fullscreen_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.action_fullscreen_inspect),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (photo.mediaType == com.example.data.model.MediaType.GIF) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Green)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "GIF Loop Active",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (photo.syncStatus != SyncStatus.SYNCED && photo.mediaType == com.example.data.model.MediaType.IMAGE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Local Only",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (slideshowPhotos.size > 1) {
                SlideshowPlayerBar(
                    isPlaying = isSlideshowPlaying,
                    currentIndex = currentSlideIndex,
                    totalCount = slideshowPhotos.size,
                    intervalSeconds = slideshowIntervalSeconds,
                    transition = slideshowTransition,
                    progress = slideshowProgress.value,
                    onTogglePlay = { viewModel.toggleSlideshow() },
                    onPrevious = { viewModel.advanceSlideshowPrevious() },
                    onNext = { viewModel.advanceSlideshowNext(manual = true) },
                    onOpenSettings = { showSlideshowSettings = true },
                    onOpenFullscreen = if (photo.mediaType != com.example.data.model.MediaType.VIDEO) {
                        { showFullscreenViewer = true }
                    } else null,
                    testTagPrefix = "hero_slideshow",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // White Rounded Sheet + Light Blue Date & Time Header Strip (Inspired by Middle Reference Screen)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val calendar = Calendar.getInstance().apply { timeInMillis = photo.timestamp }
                    val dateString = DateFormat.format("dd MMM, yyyy · h:mm a", calendar).toString()

                    // Soft Light-Blue Date & Category Info Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(LuminaInfoStripBlue)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = LuminaSkyAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Date & time",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminaOnSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = dateString,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LuminaOnSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White
                        ) {
                            Text(
                                text = photo.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LuminaVioletPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {

                // AI Caption Quote block
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gemini AI Descriptor",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = photo.description.ifBlank { "Lumina is cataloging this photo tags, wait a moment!" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Visual details card
                Text(
                    text = "Lumina library index",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (photo.category) {
                            "Nature" -> Icons.Default.Landscape
                            "People" -> Icons.Default.Face
                            "Food" -> Icons.Default.Restaurant
                            "Places" -> Icons.Default.Place
                            "Things" -> Icons.Default.Category
                            else -> Icons.Default.BubbleChart
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Category",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                photo.category,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Simulated Location Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (photo.location.isNotBlank()) photo.location else "Simulated: Global Planet Surface",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Simulated File properties Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "File specs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sizeKb = photo.storageSize / 1024
                            Text(
                                "Size: $sizeKb KB · Sync: ${photo.syncStatus.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (photo.syncStatus == SyncStatus.SYNCED && !photo.cloudUrl.isNullOrBlank()) {
                        // Synced cloud URL row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Cloud backing URL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    photo.cloudUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Semantic metadata & custom album tags row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tags & Virtual Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { showAddTagDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("add_custom_tag_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Tag")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val tagsList = photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isEmpty()) {
                    Text(
                        "No tags indexed. Tap 'Add Tag' to assign this photo to a virtual album or run AI analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    FlowRowHelper(
                        spacing = 8.dp
                    ) {
                        tagsList.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    viewModel.updateSearchQuery(tag)
                                    onBack()
                                },
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeTagFromPhoto(photo, tag) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $tag tag",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bright Sky-Blue Pill Primary Action (Inspired by Middle Reference Screen CTA)
                Button(
                    onClick = { viewModel.triggerManualAIReanalysis(photo) },
                    enabled = !isAnalyzing,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminaSkyAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("detail_primary_cta")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzing) "Analyzing Photo..." else "Analyze with Gemini AI",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
                }
            }
        }
    }

    if (showAddTagDialog) {
        var newTagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Custom Tag", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Assign a custom tag to group this photo into a virtual album.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        label = { Text("Tag name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_tag_input")
                    )
                    if (customAlbumTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Existing custom albums",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(customAlbumTags) { existing ->
                                AssistChip(
                                    onClick = {
                                        viewModel.addTagToPhoto(photo, existing)
                                        showAddTagDialog = false
                                    },
                                    label = { Text("#$existing") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addTagToPhoto(photo, newTagInput)
                        showAddTagDialog = false
                    },
                    enabled = newTagInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_tag_button")
                ) {
                    Text("Save Tag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFullscreenViewer && photo.mediaType != com.example.data.model.MediaType.VIDEO) {
        Dialog(
            onDismissRequest = { showFullscreenViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding()
            ) {
                AnimatedContent(
                    targetState = photo,
                    transitionSpec = {
                        slideshowTransitionSpec(slideshowTransition, slideshowDirection)
                    },
                    contentKey = { it.id },
                    modifier = Modifier.fillMaxSize(),
                    label = "FullscreenSlideshowTransition"
                ) { slidePhoto ->
                    val kenBurnsScale = if (isSlideshowPlaying && slideshowTransition == SlideshowTransition.ZOOM) {
                        1f + (slideshowProgress.value * 0.06f)
                    } else {
                        1f
                    }
                    ZoomablePhotoView(
                        imageUri = slidePhoto.uri,
                        contentDescription = slidePhoto.title,
                        resetKey = "${slidePhoto.id}_fullscreen",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = kenBurnsScale
                                scaleY = kenBurnsScale
                            }
                            .testTag("fullscreen_zoomable_image")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = photo.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (photo.mediaType == com.example.data.model.MediaType.IMAGE) {
                            IconButton(
                                onClick = {
                                    viewModel.pauseSlideshow()
                                    showPhotoEditor = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .testTag("fullscreen_edit_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropRotate,
                                    contentDescription = stringResource(R.string.action_edit_photo),
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { showFullscreenViewer = false },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("close_fullscreen_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close_fullscreen),
                                tint = Color.White
                            )
                        }
                    }
                }

                if (slideshowPhotos.size > 1) {
                    SlideshowPlayerBar(
                        isPlaying = isSlideshowPlaying,
                        currentIndex = currentSlideIndex,
                        totalCount = slideshowPhotos.size,
                        intervalSeconds = slideshowIntervalSeconds,
                        transition = slideshowTransition,
                        progress = slideshowProgress.value,
                        onTogglePlay = { viewModel.toggleSlideshow() },
                        onPrevious = { viewModel.advanceSlideshowPrevious() },
                        onNext = { viewModel.advanceSlideshowNext(manual = true) },
                        onOpenSettings = { showSlideshowSettings = true },
                        onOpenFullscreen = null,
                        testTagPrefix = "fullscreen_slideshow",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }

    if (showSlideshowSettings) {
        SlideshowSettingsDialog(
            intervalSeconds = slideshowIntervalSeconds,
            transition = slideshowTransition,
            loopEnabled = slideshowLoopEnabled,
            onIntervalSelected = { viewModel.setSlideshowInterval(it) },
            onTransitionSelected = { viewModel.setSlideshowTransition(it) },
            onLoopToggled = { viewModel.setSlideshowLoopEnabled(it) },
            onDismiss = { showSlideshowSettings = false }
        )
    }

    if (showPhotoEditor && photo.mediaType == com.example.data.model.MediaType.IMAGE) {
        PhotoEditorDialog(
            photo = photo,
            isSaving = isSavingEdit,
            onSave = { sourceBitmap, rotationDegrees, cropRect, saveAsCopy ->
                viewModel.saveEditedPhoto(
                    photo = photo,
                    sourceBitmap = sourceBitmap,
                    rotationDegrees = rotationDegrees,
                    cropRect = cropRect,
                    saveAsCopy = saveAsCopy,
                    onComplete = { saved ->
                        if (saved) {
                            showPhotoEditor = false
                            if (viewModel.recordPhotoExportAndCheckInterstitial() && activity != null) {
                                LevelPlayManager.showInterstitialIfReady(activity) {
                                    viewModel.markExportInterstitialShown()
                                }
                            }
                        }
                    }
                )
            },
            onDismiss = { showPhotoEditor = false }
        )
    }
}

// Simple flowing elements wrapping helper to mimic a Wrap Column/Row in Compose easily
@Composable
fun FlowRowHelper(
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    // Standard Horizontal Flow wrapping
    // Since flow-layouts was recently added, we can simulate a flow row by breaking rows or using simple LazyRow.
    // However, to keep it beautifully wrap-lined, we can put them in a simple scrollable horizontal row or vertical layout.
    // LazyRow is extremely robust and avoids compile errors across custom compose-foundation versions.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                content()
            }
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }

    androidx.compose.runtime.DisposableEffect(videoUrl) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Error stopping video playback: ${e.message}")
            }
            videoViewRef = null
            mediaPlayerRef = null
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.widget.VideoView(context).apply {
                    videoViewRef = this
                    setVideoPath(videoUrl)
                    setOnErrorListener { mp, what, extra ->
                        android.util.Log.e("VideoPlayer", "Error playing media: what=$what, extra=$extra. Gracefully absorbing to prevent app dialog crash.")
                        true // Handled, avoids showing the default android media error dialog box
                    }
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        try {
                            mp.isLooping = true
                            if (isMuted) {
                                mp.setVolume(0f, 0f)
                            } else {
                                mp.setVolume(1f, 1f)
                            }
                            duration = mp.duration
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPlayer", "Error preparing media properties: ${e.message}")
                        }
                        start()
                        isPlaying = true
                    }
                }
            },
            update = { videoView ->
                // Updating is handled in callback listeners
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay controller controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isPlaying) 0f else 0.35f))
        ) {
            // Central Play/Pause toggle overlay circle
            IconButton(
                onClick = {
                    try {
                        videoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayer", "Error toggling play/pause: ${e.message}")
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Bottom sound and tag bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video loop icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lumina Streaming Loop",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            isMuted = !isMuted
                            mediaPlayerRef?.let { mp ->
                                if (isMuted) {
                                    mp.setVolume(0f, 0f)
                                } else {
                                    mp.setVolume(1f, 1f)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPlayer", "Error toggling mute: ${e.message}")
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Toggle sound" else "Mute sound",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePhotoView(
    imageUri: String,
    contentDescription: String,
    resetKey: Any,
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    doubleTapScale: Float = 2.5f
) {
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    LaunchedEffect(resetKey) {
        scaleAnim.snapTo(1f)
        offsetAnim.snapTo(Offset.Zero)
    }

    fun clampOffset(targetOffset: Offset, scale: Float): Offset {
        if (scale <= 1.01f || containerSize.width == 0 || containerSize.height == 0) {
            return Offset.Zero
        }
        val maxX = (containerSize.width * (scale - 1f)) / 2f
        val maxY = (containerSize.height * (scale - 1f)) / 2f
        return Offset(
            x = targetOffset.x.coerceIn(-maxX, maxX),
            y = targetOffset.y.coerceIn(-maxY, maxY)
        )
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val oldScale = scaleAnim.value
        val newScale = (oldScale * zoomChange).coerceIn(minScale, maxScale)
        val scaledOffset = if (oldScale > 0f) offsetAnim.value * (newScale / oldScale) else Offset.Zero
        val nextOffset = clampOffset(scaledOffset + panChange, newScale)

        coroutineScope.launch {
            scaleAnim.snapTo(newScale)
            offsetAnim.snapTo(nextOffset)
        }
    }

    val isZoomed = scaleAnim.value > 1.05f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .onSizeChanged { containerSize = it }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val currentScale = scaleAnim.value
                        val targetScale = if (currentScale > 1.05f) minScale else doubleTapScale
                        val targetOffset = if (targetScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            val center = Offset(
                                x = containerSize.width / 2f,
                                y = containerSize.height / 2f
                            )
                            val deltaFromCenter = tapOffset - center
                            clampOffset(-deltaFromCenter * (targetScale - 1f), targetScale)
                        }
                        coroutineScope.launch {
                            launch {
                                scaleAnim.animateTo(
                                    targetValue = targetScale,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                offsetAnim.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }
                )
            }
            .transformable(
                state = transformableState,
                canPan = { _ -> scaleAnim.value > 1.01f }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetAnim.value.x
                    translationY = offsetAnim.value.y
                }
        )

        AnimatedVisibility(
            visible = isZoomed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Surface(
                onClick = {
                    coroutineScope.launch {
                        launch {
                            scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                        launch {
                            offsetAnim.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("zoom_reset_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = stringResource(R.string.action_reset_zoom),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1fx • Tap to reset", scaleAnim.value),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

