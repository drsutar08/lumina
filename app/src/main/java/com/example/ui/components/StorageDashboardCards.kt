package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.theme.PastelBlueTile
import com.example.ui.theme.PastelLavenderTile
import com.example.ui.theme.PastelMintTile
import com.example.ui.theme.PastelPeachTile
import com.example.ui.viewmodel.StorageStats

private val SegmentPhotosColor = LuminaVioletPrimary
private val SegmentVideosColor = LuminaSkyAccent
private val SegmentDocsColor = Color(0xFFF59E0B)
private val SegmentGifsColor = Color(0xFF10B981)

data class StorageCategorySegment(
    val label: String,
    val bytes: Long,
    val formattedSize: String,
    val color: Color
)

@Composable
fun DashboardStorageSection(
    storageStats: StorageStats,
    onManageStorageClick: () -> Unit,
    onQuickSyncClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val photosLabel = stringResource(R.string.storage_cat_photos)
    val videosLabel = stringResource(R.string.storage_cat_videos)
    val documentsLabel = stringResource(R.string.storage_cat_documents)
    val gifsLabel = stringResource(R.string.storage_cat_gifs)

    val cloudPercent = (storageStats.percentUsed.coerceIn(0f, 1f) * 100).toInt()
    val devicePercent = (storageStats.devicePercentUsed.coerceIn(0f, 1f) * 100).toInt()
    val libraryTint = if (storageStats.localUnsyncedCount == 0) PastelMintTile else PastelPeachTile
    val libraryAccent = if (storageStats.localUnsyncedCount == 0) Color(0xFF1F7A4D) else Color(0xFFC25E28)

    val cloudSegments = listOf(
        StorageCategorySegment(
            label = photosLabel,
            bytes = storageStats.syncedPhotosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.syncedPhotosBytes),
            color = SegmentPhotosColor
        ),
        StorageCategorySegment(
            label = videosLabel,
            bytes = storageStats.syncedVideosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.syncedVideosBytes),
            color = SegmentVideosColor
        ),
        StorageCategorySegment(
            label = documentsLabel,
            bytes = storageStats.cloudDocumentsBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.cloudDocumentsBytes),
            color = SegmentDocsColor
        )
    )

    val deviceSegments = listOf(
        StorageCategorySegment(
            label = photosLabel,
            bytes = storageStats.photosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.photosBytes),
            color = SegmentPhotosColor
        ),
        StorageCategorySegment(
            label = videosLabel,
            bytes = storageStats.videosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.videosBytes),
            color = SegmentVideosColor
        ),
        StorageCategorySegment(
            label = documentsLabel,
            bytes = storageStats.deviceDocumentsAndOtherBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.deviceDocumentsAndOtherBytes),
            color = SegmentDocsColor
        )
    )

    val librarySegments = listOf(
        StorageCategorySegment(
            label = photosLabel,
            bytes = storageStats.photosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.photosBytes),
            color = SegmentPhotosColor
        ),
        StorageCategorySegment(
            label = videosLabel,
            bytes = storageStats.videosBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.videosBytes),
            color = SegmentVideosColor
        ),
        StorageCategorySegment(
            label = gifsLabel,
            bytes = storageStats.gifsAndDocsBytes,
            formattedSize = storageStats.formatStorageBytes(storageStats.gifsAndDocsBytes),
            color = SegmentGifsColor
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("dashboard_storage_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = LuminaVioletPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.storage_overview_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LuminaOnSurface
                )
            }

            Surface(
                onClick = onManageStorageClick,
                shape = RoundedCornerShape(50),
                color = LuminaVioletPrimary.copy(alpha = 0.08f),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("dashboard_storage_manage_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.storage_manage_action),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LuminaVioletPrimary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = LuminaVioletPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWideLayout = maxWidth >= 560.dp

            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StorageMetricCard(
                        title = stringResource(R.string.storage_cloud_title),
                        usedVsTotalText = "${storageStats.formatStorageBytes(storageStats.usedBytesCloud)} / ${storageStats.formatStorageBytes(storageStats.totalLimitCloud)}",
                        badgeText = "$cloudPercent%",
                        usedBytes = storageStats.usedBytesCloud,
                        totalBytes = storageStats.totalLimitCloud,
                        segments = cloudSegments,
                        footerNote = "${storageStats.syncedCount} backed up",
                        icon = Icons.Default.CloudQueue,
                        containerColor = PastelLavenderTile,
                        accentColor = LuminaVioletPrimary,
                        onClick = onManageStorageClick,
                        testTagPrefix = "dashboard_cloud_storage",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_cloud_storage_card")
                    )

                    StorageMetricCard(
                        title = stringResource(R.string.storage_device_title),
                        usedVsTotalText = "${storageStats.formatStorageBytes(storageStats.deviceUsedBytes)} / ${storageStats.formatStorageBytes(storageStats.deviceTotalBytes)}",
                        badgeText = "$devicePercent%",
                        usedBytes = storageStats.deviceUsedBytes,
                        totalBytes = storageStats.deviceTotalBytes,
                        segments = deviceSegments,
                        footerNote = "${storageStats.formatStorageBytes(storageStats.deviceAvailableBytes)} free",
                        icon = Icons.Default.Smartphone,
                        containerColor = PastelBlueTile,
                        accentColor = LuminaSkyAccent,
                        onClick = onManageStorageClick,
                        testTagPrefix = "dashboard_device_storage",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_device_storage_card")
                    )

                    StorageMetricCard(
                        title = stringResource(R.string.storage_library_title),
                        usedVsTotalText = "${storageStats.formatStorageBytes(storageStats.totalLibraryBytes)} • ${storageStats.totalPhotoCount} items",
                        badgeText = if (storageStats.localUnsyncedCount > 0) "${storageStats.localUnsyncedCount} local" else "Synced",
                        usedBytes = storageStats.totalLibraryBytes,
                        totalBytes = storageStats.totalLibraryBytes.coerceAtLeast(1L),
                        segments = librarySegments,
                        footerNote = if (storageStats.localUnsyncedCount > 0) {
                            "${storageStats.formatStorageBytes(storageStats.localUnsyncedBytes)} pending sync"
                        } else {
                            "All memories backed up"
                        },
                        icon = if (storageStats.localUnsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.PhotoLibrary,
                        containerColor = libraryTint,
                        accentColor = libraryAccent,
                        onClick = {
                            if (storageStats.localUnsyncedCount > 0 && onQuickSyncClick != null) {
                                onQuickSyncClick()
                            } else {
                                onManageStorageClick()
                            }
                        },
                        testTagPrefix = "dashboard_library_storage",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_library_storage_card")
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StorageMetricCard(
                            title = stringResource(R.string.storage_cloud_title),
                            usedVsTotalText = "${storageStats.formatStorageBytes(storageStats.usedBytesCloud)} / ${storageStats.formatStorageBytes(storageStats.totalLimitCloud)}",
                            badgeText = "$cloudPercent%",
                            usedBytes = storageStats.usedBytesCloud,
                            totalBytes = storageStats.totalLimitCloud,
                            segments = cloudSegments,
                            footerNote = "${storageStats.syncedCount} backed up",
                            icon = Icons.Default.CloudQueue,
                            containerColor = PastelLavenderTile,
                            accentColor = LuminaVioletPrimary,
                            onClick = onManageStorageClick,
                            testTagPrefix = "dashboard_cloud_storage",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_cloud_storage_card")
                        )

                        StorageMetricCard(
                            title = stringResource(R.string.storage_device_title),
                            usedVsTotalText = "${storageStats.formatStorageBytes(storageStats.deviceUsedBytes)} / ${storageStats.formatStorageBytes(storageStats.deviceTotalBytes)}",
                            badgeText = "$devicePercent%",
                            usedBytes = storageStats.deviceUsedBytes,
                            totalBytes = storageStats.deviceTotalBytes,
                            segments = deviceSegments,
                            footerNote = "${storageStats.formatStorageBytes(storageStats.deviceAvailableBytes)} free",
                            icon = Icons.Default.Smartphone,
                            containerColor = PastelBlueTile,
                            accentColor = LuminaSkyAccent,
                            onClick = onManageStorageClick,
                            testTagPrefix = "dashboard_device_storage",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_device_storage_card")
                        )
                    }

                    // Full-width Library Breakdown Storage Card
                    LibraryBreakdownCard(
                        storageStats = storageStats,
                        segments = librarySegments,
                        containerColor = libraryTint,
                        accentColor = libraryAccent,
                        onCardClick = onManageStorageClick,
                        onQuickSyncClick = onQuickSyncClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageMetricCard(
    title: String,
    usedVsTotalText: String,
    badgeText: String,
    usedBytes: Long,
    totalBytes: Long,
    segments: List<StorageCategorySegment>,
    footerNote: String,
    icon: ImageVector,
    containerColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f)),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = LuminaOnSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = usedVsTotalText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LuminaOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SegmentedStorageProgressBar(
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                segments = segments,
                contentDescription = "$title: $usedVsTotalText",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_progress_bar")
            )

            // Categorized breakdown legend rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_breakdown"),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                segments.forEach { segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(segment.color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = segment.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = LuminaOnSurface.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = segment.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LuminaOnSurface
                        )
                    }
                }
            }

            Text(
                text = footerNote,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = LuminaOnSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryBreakdownCard(
    storageStats: StorageStats,
    segments: List<StorageCategorySegment>,
    containerColor: Color,
    accentColor: Color,
    onCardClick: () -> Unit,
    onQuickSyncClick: (() -> Unit)?
) {
    val syncedBytes = (storageStats.totalLibraryBytes - storageStats.localUnsyncedBytes).coerceAtLeast(0L)
    val totalOrMin = storageStats.totalLibraryBytes.coerceAtLeast(1L)

    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("dashboard_library_storage_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (storageStats.localUnsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.storage_library_title)} • ${storageStats.formatStorageBytes(storageStats.totalLibraryBytes)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = LuminaOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (storageStats.localUnsyncedCount > 0) {
                            "${storageStats.formatStorageBytes(syncedBytes)} synced • ${storageStats.formatStorageBytes(storageStats.localUnsyncedBytes)} local"
                        } else {
                            "${storageStats.totalPhotoCount} items • All backed up"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = LuminaOnSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (storageStats.localUnsyncedCount > 0 && onQuickSyncClick != null) {
                    Surface(
                        onClick = onQuickSyncClick,
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("dashboard_library_quick_sync_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Sync local items",
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White
                    ) {
                        Text(
                            text = "${storageStats.totalPhotoCount} items",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            SegmentedStorageProgressBar(
                usedBytes = storageStats.totalLibraryBytes,
                totalBytes = totalOrMin,
                segments = segments,
                contentDescription = "Photo library storage breakdown",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_library_storage_progress_bar")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_library_storage_breakdown"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                segments.forEach { segment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(segment.color)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${segment.label} ${segment.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = LuminaOnSurface.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedStorageProgressBar(
    usedBytes: Long,
    totalBytes: Long,
    segments: List<StorageCategorySegment>,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalBytes.coerceAtLeast(1L)
    val usedFraction = (usedBytes.coerceIn(0L, safeTotal).toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(9.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.82f))
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalWidth = size.width
            val height = size.height
            val usedWidth = totalWidth * usedFraction
            if (usedWidth <= 0f) return@Canvas

            val nonZero = segments.filter { it.bytes > 0L }
            if (nonZero.isEmpty()) {
                drawRect(
                    color = LuminaVioletPrimary,
                    topLeft = Offset.Zero,
                    size = Size(usedWidth, height)
                )
                return@Canvas
            }

            val sumSegmentBytes = nonZero.sumOf { it.bytes }.coerceAtLeast(1L).toFloat()
            val gapPx = 1.5.dp.toPx()
            val totalGapPx = gapPx * (nonZero.size - 1).coerceAtLeast(0)
            val drawableWidth = (usedWidth - totalGapPx).coerceAtLeast(0f)
            val minSliverPx = if (drawableWidth >= 4.dp.toPx() * nonZero.size) 3.dp.toPx() else 0f

            // Allocate at least a visible sliver per non-zero category while preserving total width
            val rawWidths = nonZero.map { seg ->
                (seg.bytes.toFloat() / sumSegmentBytes) * drawableWidth
            }
            val adjustedWidths = if (minSliverPx > 0f) {
                val boosted = rawWidths.map { w -> if (w < minSliverPx) minSliverPx else w }
                val sumBoosted = boosted.sum().coerceAtLeast(1f)
                boosted.map { it * (drawableWidth / sumBoosted) }
            } else {
                rawWidths
            }

            var currentX = 0f
            nonZero.forEachIndexed { index, segment ->
                val segWidth = adjustedWidths[index].coerceAtLeast(0f)
                if (segWidth > 0f) {
                    drawRect(
                        color = segment.color,
                        topLeft = Offset(currentX, 0f),
                        size = Size(segWidth, height)
                    )
                }
                currentX += segWidth + gapPx
            }
        }
    }
}
