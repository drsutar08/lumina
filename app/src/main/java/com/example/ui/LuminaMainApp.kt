package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ads.LevelPlayManager
import com.example.data.local.MediaStorePhotoLoader
import com.example.ui.components.AdConsentDialog
import com.example.ui.components.LevelPlayBannerDock
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.PhotoDetailsScreen
import com.example.ui.screens.PhotosFeedScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.viewmodel.PhotosViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LuminaMainApp(
    viewModel: PhotosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { MediaStorePhotoLoader.findActivity(context) }
    val selectedPhoto by viewModel.selectedPhoto.collectAsState()
    val adState by viewModel.adMonetizationState.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    var currentTab by remember { mutableStateOf(LuminaTab.PHOTOS) }

    LaunchedEffect(adState.consentState, adState.isAdFreeEnabled) {
        LevelPlayManager.initializeIfEligible(
            context = context,
            consentState = adState.consentState,
            isAdFreeEnabled = adState.isAdFreeEnabled,
            activity = activity
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (selectedPhoto != null) {
            // Photo Details screen is active (fullscreen overlays tabs)
            PhotoDetailsScreen(
                viewModel = viewModel,
                onBack = { viewModel.selectPhoto(null) }
            )
        } else {
            // Normal tab navigation is active
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LevelPlayBannerDock(
                            visible = adState.shouldShowAds && !isSelectionMode
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lumina_bottom_nav"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            LuminaTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = LuminaSkyAccent,
                                        selectedTextColor = LuminaSkyAccent,
                                        indicatorColor = LuminaSkyAccent.copy(alpha = 0.14f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // Fluid crossfade between screens based on currentTab
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        LuminaTab.PHOTOS -> {
                            PhotosFeedScreen(
                                viewModel = viewModel,
                                onPhotoSelected = { viewModel.selectPhoto(it) },
                                onNavigateToCloudSync = { currentTab = LuminaTab.CLOUD },
                                onSearchClicked = { currentTab = LuminaTab.SEARCH }
                            )
                        }
                        LuminaTab.ALBUMS -> {
                            AlbumsScreen(
                                viewModel = viewModel,
                                onPhotoSelected = { viewModel.selectPhoto(it) }
                            )
                        }
                        LuminaTab.SEARCH -> {
                            SearchScreen(
                                viewModel = viewModel,
                                onPhotoSelected = { viewModel.selectPhoto(it) }
                            )
                        }
                        LuminaTab.CLOUD -> {
                            CloudSyncScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        if (adState.shouldPromptConsent && !adState.isAdFreeEnabled) {
            AdConsentDialog(
                onAcceptPersonalized = { viewModel.submitAdConsent(personalized = true) },
                onAcceptNonPersonalized = { viewModel.submitAdConsent(personalized = false) },
                onEnableAdFree = {
                    viewModel.submitAdConsent(personalized = false)
                    viewModel.setAdFreeEnabled(true)
                }
            )
        }
    }
}

enum class LuminaTab(
    val title: String,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    PHOTOS(
        "Photos",
        Icons.Filled.InsertPhoto,
        Icons.Outlined.InsertPhoto
    ),
    ALBUMS(
        "Albums",
        Icons.Filled.PhotoAlbum,
        Icons.Outlined.PhotoAlbum
    ),
    SEARCH(
        "Search",
        Icons.Filled.Search,
        Icons.Outlined.Search
    ),
    CLOUD(
        "Cloud Sync",
        Icons.Filled.CloudQueue,
        Icons.Outlined.CloudQueue
    )
}
