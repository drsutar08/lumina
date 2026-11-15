package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Photo
import com.example.ui.components.BotanicalAccentCanvas
import com.example.ui.components.CloudSyncIndicatorButton
import com.example.ui.components.EmptyStateIllustrationView
import com.example.ui.components.TopAppBarSearchTextField
import com.example.ui.theme.LuminaIceBlueBackdrop
import com.example.ui.theme.LuminaLavenderBackdrop
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.PhotosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: PhotosViewModel,
    onPhotoSelected: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredPhotos by viewModel.filteredPhotos.collectAsState()
    val isExpandingSearch by viewModel.isExpandingSearch.collectAsState()
    val expandedTags by viewModel.expandedTags.collectAsState()
    val suggestedAlbums by viewModel.suggestedAlbums.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LuminaLavenderBackdrop,
        topBar = {
            TopAppBar(
                title = {
                    TopAppBarSearchTextField(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onSearchSubmit = { viewModel.submitSemanticSearch() },
                        isSearching = isExpandingSearch,
                        testTagName = "ai_search_bar"
                    )
                },
                actions = {
                    CloudSyncIndicatorButton(
                        syncState = cloudSyncState,
                        onClick = { viewModel.triggerBackupSync(forceReverify = true) },
                        showLabel = false,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuminaLavenderBackdrop
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(LuminaLavenderBackdrop, LuminaIceBlueBackdrop)
                    )
                )
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BotanicalAccentCanvas(
                    lineColor = LuminaVioletPrimary.copy(alpha = 0.22f),
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp)
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LuminaOnSurface
                    )
                    Text(
                        text = "Semantic visual search powered by Lumina AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = LuminaOnSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
            // Semantic Suggestions
            AnimatedVisibility(
                visible = isExpandingSearch || expandedTags.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Suggestions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExpandingSearch) "Gemini is expanding your query..." else "AI Semantic Suggestions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (expandedTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(expandedTags) { tag ->
                                AssistChip(
                                    onClick = { viewModel.updateSearchQuery(tag) },
                                    label = { Text(tag) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (searchQuery.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 88.dp)
                ) {
                    // --- AI Suggested Smart Albums Section ---
                    if (suggestedAlbums.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI Suggested Smart Albums",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "By Theme / Location",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(suggestedAlbums, key = { it.id }) { album ->
                                    Card(
                                        modifier = Modifier
                                            .width(134.dp)
                                            .clickable { viewModel.updateSearchQuery(album.searchQuery) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = album.coverPhotoUrl,
                                                    contentDescription = album.title,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        .align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = when (album.type) {
                                                            "LOCATION" -> "Vacation"
                                                            "DATE" -> "Compilation"
                                                            else -> "AI Theme"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                            
                                            Column(
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                Text(
                                                    text = album.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = album.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }

                    // Popular Category Clusters (Standard Photos layout)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "People & Pets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SearchClusterCircle(
                                label = "Friendly Puppy",
                                icon = Icons.Default.Pets,
                                backgroundColor = Color(0xFFFFECEC),
                                tint = Color(0xFFE91E63),
                                onClick = { viewModel.updateSearchQuery("dog") }
                            )
                            SearchClusterCircle(
                                label = "Group Friends",
                                icon = Icons.Default.Face,
                                backgroundColor = Color(0xFFE3F2FD),
                                tint = Color(0xFF1E88E5),
                                onClick = { viewModel.updateSearchQuery("friends") }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Explore Places",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SearchClusterCircle(
                                label = "Coast & Beach",
                                icon = Icons.Default.Landscape,
                                backgroundColor = Color(0xFFE8F5E9),
                                tint = Color(0xFF4CAF50),
                                onClick = { viewModel.updateSearchQuery("beach") }
                            )
                            SearchClusterCircle(
                                label = "Neon Streets",
                                icon = Icons.Default.HomeWork,
                                backgroundColor = Color(0xFFECEFF1),
                                tint = Color(0xFF607D8B),
                                onClick = { viewModel.updateSearchQuery("tokyo") }
                            )
                            SearchClusterCircle(
                                label = "Cosy Cabin",
                                icon = Icons.Default.Cabin,
                                backgroundColor = Color(0xFFFFF3E0),
                                tint = Color(0xFFE65100),
                                onClick = { viewModel.updateSearchQuery("cabin") }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Smart Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SearchClusterCircle(
                                label = "Food Platter",
                                icon = Icons.Default.Restaurant,
                                backgroundColor = Color(0xFFF3E5F5),
                                tint = Color(0xFF9C27B0),
                                onClick = { viewModel.updateSearchQuery("pizza") }
                            )
                            SearchClusterCircle(
                                label = "Vint Tech",
                                icon = Icons.Default.PhotoCamera,
                                backgroundColor = Color(0xFFE0F7FA),
                                tint = Color(0xFF00ACC1),
                                onClick = { viewModel.updateSearchQuery("camera") }
                            )
                        }
                    }
                }
            } else {
                // Search result grids
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Found ${filteredPhotos.size} results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    if (filteredPhotos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateIllustrationView(
                                drawableRes = R.drawable.img_empty_search,
                                imageContentDescription = "No search results illustration",
                                title = "No Matching Memories",
                                message = "We couldn't find photos matching \"$searchQuery\". Try another keyword, year, or custom tag.",
                                actionLabel = "Clear Search",
                                onActionClick = { viewModel.updateSearchQuery("") },
                                testTagPrefix = "empty_search"
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filteredPhotos, key = { it.id }) { photo ->
                                PhotoGridTile(
                                    photo = photo,
                                    onPhotoSelected = onPhotoSelected,
                                    onFavoriteToggled = { viewModel.toggleFavorite(photo) }
                                )
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

@Composable
fun SearchClusterCircle(
    label: String,
    icon: ImageVector,
    backgroundColor: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
