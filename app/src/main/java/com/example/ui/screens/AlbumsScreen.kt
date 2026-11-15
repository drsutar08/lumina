package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.Photo
import com.example.ui.components.BotanicalAccentCanvas
import com.example.ui.components.CloudSyncIndicatorButton
import com.example.ui.components.EmptyStateIllustrationView
import com.example.ui.theme.LuminaIceBlueBackdrop
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.PastelBlueTile
import com.example.ui.theme.PastelLavenderTile
import com.example.ui.theme.PastelMintTile
import com.example.ui.theme.PastelPeachTile
import com.example.ui.viewmodel.AlbumGroupingMode
import com.example.ui.viewmodel.PhotosViewModel
import com.example.ui.viewmodel.VirtualAlbum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: PhotosViewModel,
    onPhotoSelected: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val dateAlbums by viewModel.dateVirtualAlbums.collectAsState()
    val tagAlbums by viewModel.tagVirtualAlbums.collectAsState()
    val selectedAlbumId by viewModel.selectedVirtualAlbumId.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()

    var activeGrouping by rememberSaveable { mutableStateOf(AlbumGroupingMode.DATE) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showAssignPhotosDialog by remember { mutableStateOf(false) }

    val displayedAlbums = when (activeGrouping) {
        AlbumGroupingMode.DATE -> dateAlbums
        AlbumGroupingMode.TAGS -> tagAlbums
    }

    val selectedAlbum = remember(selectedAlbumId, dateAlbums, tagAlbums) {
        (dateAlbums + tagAlbums).firstOrNull { it.id == selectedAlbumId }
    }

    if (selectedAlbum != null) {
        BackHandler {
            viewModel.selectVirtualAlbum(null)
        }

        VirtualAlbumDetailView(
            album = selectedAlbum,
            onBack = { viewModel.selectVirtualAlbum(null) },
            onPhotoSelected = onPhotoSelected,
            onFavoriteToggled = { viewModel.toggleFavorite(it) },
            onAddPhotosClicked = { showAssignPhotosDialog = true },
            onDeleteAlbumClicked = {
                viewModel.deleteCustomTagAlbum(selectedAlbum.tagKey)
                viewModel.selectVirtualAlbum(null)
            },
            modifier = modifier
        )

        if (showAssignPhotosDialog) {
            val existingIds = remember(selectedAlbum) { selectedAlbum.photos.map { it.id }.toSet() }
            SelectPhotosForAlbumDialog(
                title = "Add Photos to #${selectedAlbum.title}",
                allPhotos = allPhotos,
                preselectedIds = existingIds,
                onDismiss = { showAssignPhotosDialog = false },
                onConfirm = { pickedIds ->
                    viewModel.assignPhotosToTagAlbum(selectedAlbum.tagKey, pickedIds)
                    showAssignPhotosDialog = false
                }
            )
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LuminaIceBlueBackdrop,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateAlbumDialog = true },
                containerColor = LuminaSkyAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .testTag("new_custom_album_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_new_album),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LuminaIceBlueBackdrop)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // --- Soft Ice-Blue Header Zone (Inspired by Right Reference Screen) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BotanicalAccentCanvas(
                    lineColor = LuminaSkyAccent.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(76.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.virtual_albums_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = LuminaOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CloudSyncIndicatorButton(
                                syncState = cloudSyncState,
                                onClick = { viewModel.triggerBackupSync(forceReverify = true) },
                                showLabel = false
                            )
                            TextButton(
                                onClick = { showCreateAlbumDialog = true },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("create_virtual_album_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = LuminaSkyAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_new_album),
                                    color = LuminaSkyAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pill Segmented Switcher (Inspired by Habits / Tasks pill toggle in reference)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Triple(AlbumGroupingMode.DATE, R.string.group_by_date, Icons.Default.CalendarMonth),
                                Triple(AlbumGroupingMode.TAGS, R.string.group_by_tags, Icons.Default.Tag)
                            ).forEach { (mode, labelRes, icon) ->
                                val isSelected = activeGrouping == mode
                                Surface(
                                    onClick = { activeGrouping = mode },
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) LuminaSkyAccent else Color.Transparent,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .testTag(if (mode == AlbumGroupingMode.DATE) "album_group_date" else "album_group_tags")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (displayedAlbums.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Quick horizontal pill timeline/tag strip inspired by the reference calendar row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(displayedAlbums.take(8), key = { "pill_${it.id}" }) { album ->
                                val isFirst = displayedAlbums.firstOrNull()?.id == album.id
                                Surface(
                                    onClick = { viewModel.selectVirtualAlbum(album.id) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isFirst) LuminaOnSurface else Color.White.copy(alpha = 0.85f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${album.photoCount}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFirst) Color.White else LuminaOnSurface
                                        )
                                        Text(
                                            text = album.title.take(10),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFirst) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- White Rounded Sheet with Pastel 2-Column Virtual Album Cards ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterHorizontally)
                    )

                    if (displayedAlbums.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateIllustrationView(
                                drawableRes = R.drawable.img_empty_gallery,
                                imageContentDescription = "Empty virtual albums illustration",
                                title = "No Virtual Albums Yet",
                                message = "Add photos or create a custom tag album to group your gallery by date or theme.",
                                actionLabel = stringResource(R.string.action_new_album),
                                onActionClick = { showCreateAlbumDialog = true },
                                testTagPrefix = "empty_albums"
                            )
                        }
                    } else {
                        val pastelTiles = remember {
                            listOf(PastelBlueTile, PastelLavenderTile, PastelMintTile, PastelPeachTile)
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .testTag("virtual_albums_grid"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedAlbums.size, key = { idx -> displayedAlbums[idx].id }) { index ->
                                val album = displayedAlbums[index]
                                PastelVirtualAlbumCard(
                                    album = album,
                                    tileColor = pastelTiles[index % pastelTiles.size],
                                    onClick = { viewModel.selectVirtualAlbum(album.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateAlbumDialog) {
        CreateCustomTagAlbumDialog(
            allPhotos = allPhotos,
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = { tagName, selectedIds ->
                viewModel.createCustomTagAlbum(tagName, selectedIds)
                activeGrouping = AlbumGroupingMode.TAGS
                showCreateAlbumDialog = false
            }
        )
    }
}

@Composable
private fun PastelVirtualAlbumCard(
    album: VirtualAlbum,
    tileColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("virtual_album_card_${album.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Circular Photo Thumbnail (Inspired by Right Reference Screen 2x2 cards)
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUri.isNotBlank()) {
                    AsyncImage(
                        model = remember(album.coverUri) {
                            ImageRequest.Builder(context)
                                .data(album.coverUri)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = LuminaOnSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LuminaOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // White Pill Tag Badge (Inspired by "Health", "Adventure", "Meditation" pills)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White
            ) {
                Text(
                    text = when {
                        album.groupingMode == AlbumGroupingMode.DATE -> "${album.photoCount} photos"
                        album.isCustomTag -> "Custom • ${album.photoCount}"
                        else -> "Tag • ${album.photoCount}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirtualAlbumDetailView(
    album: VirtualAlbum,
    onBack: () -> Unit,
    onPhotoSelected: (Photo) -> Unit,
    onFavoriteToggled: (Photo) -> Unit,
    onAddPhotosClicked: () -> Unit,
    onDeleteAlbumClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (album.groupingMode == AlbumGroupingMode.TAGS) "#${album.title}" else album.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = album.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("album_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back to Albums")
                    }
                },
                actions = {
                    if (album.groupingMode == AlbumGroupingMode.TAGS) {
                        IconButton(
                            onClick = onAddPhotosClicked,
                            modifier = Modifier.testTag("album_add_photos_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photos to album")
                        }
                        if (album.isCustomTag) {
                            IconButton(
                                onClick = onDeleteAlbumClicked,
                                modifier = Modifier.testTag("album_delete_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete custom album")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (album.photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This album is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddPhotosClicked,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assign Photos")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 2.dp)
                    .testTag("album_detail_grid"),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)
            ) {
                items(album.photos, key = { it.id }) { photo ->
                    PhotoGridTile(
                        photo = photo,
                        onPhotoSelected = onPhotoSelected,
                        onFavoriteToggled = { onFavoriteToggled(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCustomTagAlbumDialog(
    allPhotos: List<Photo>,
    onDismiss: () -> Unit,
    onCreate: (String, Set<Long>) -> Unit
) {
    val context = LocalContext.current
    var albumName by remember { mutableStateOf("") }
    var selectedPhotoIds by remember { mutableStateOf(emptySet<Long>()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.create_album_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Photos with this tag will automatically group into this virtual album.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text(stringResource(R.string.create_album_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_album_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (allPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Include photos (${selectedPhotoIds.size} selected)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allPhotos.take(30), key = { it.id }) { photo ->
                            val isSelected = photo.id in selectedPhotoIds
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedPhotoIds = if (isSelected) {
                                            selectedPhotoIds - photo.id
                                        } else {
                                            selectedPhotoIds + photo.id
                                        }
                                    }
                            ) {
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
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(albumName, selectedPhotoIds) },
                        enabled = albumName.isNotBlank(),
                        modifier = Modifier.testTag("confirm_create_album_button")
                    ) {
                        Text("Create Album")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectPhotosForAlbumDialog(
    title: String,
    allPhotos: List<Photo>,
    preselectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(preselectedIds) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allPhotos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selectedIds
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                }
                        ) {
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
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selectedIds) }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
