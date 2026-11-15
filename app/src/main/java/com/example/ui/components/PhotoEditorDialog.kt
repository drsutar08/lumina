package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.CropAspectRatio
import com.example.data.local.ImageEditProcessor
import com.example.data.local.NormalizedCropRect
import com.example.data.model.Photo
import com.example.ui.theme.LuminaSkyAccent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropDragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    MOVE
}

@Composable
fun PhotoEditorDialog(
    photo: Photo,
    isSaving: Boolean,
    onSave: (sourceBitmap: Bitmap, rotationDegrees: Int, cropRect: NormalizedCropRect, saveAsCopy: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember(photo.id, photo.uri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.id, photo.uri) { mutableStateOf(true) }
    var loadFailed by remember(photo.id, photo.uri) { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(photo.id, photo.uri, retryTrigger) {
        isLoading = true
        loadFailed = false
        val loaded = ImageEditProcessor.loadEditableBitmap(context, photo.uri)
        sourceBitmap = loaded
        loadFailed = loaded == null
        isLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = Color(0xFF0D1018)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = LuminaSkyAccent)
                            Text(
                                text = stringResource(R.string.editor_loading_image),
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                loadFailed || sourceBitmap == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.editor_load_failed),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text(stringResource(R.string.action_cancel), color = Color.White)
                                }
                                Button(
                                    onClick = { retryTrigger++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuminaSkyAccent),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                else -> {
                    val loadedBitmap = sourceBitmap!!
                    PhotoEditorContent(
                        sourceBitmap = loadedBitmap,
                        isSaving = isSaving,
                        onSave = { rotationDegrees, cropRect, saveAsCopy ->
                            onSave(loadedBitmap, rotationDegrees, cropRect, saveAsCopy)
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoEditorContent(
    sourceBitmap: Bitmap,
    isSaving: Boolean,
    onSave: (rotationDegrees: Int, cropRect: NormalizedCropRect, saveAsCopy: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationDegrees by remember(sourceBitmap) { mutableIntStateOf(0) }
    var selectedAspectRatio by remember(sourceBitmap) { mutableStateOf(CropAspectRatio.FREE) }
    var cropRect by remember(sourceBitmap) { mutableStateOf(NormalizedCropRect()) }

    val previewSource = remember(sourceBitmap) {
        val maxPreview = 1024
        val w = sourceBitmap.width
        val h = sourceBitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxPreview) {
            sourceBitmap
        } else {
            val scale = maxPreview.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(
                sourceBitmap,
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
                true
            )
        }
    }

    val rotatedPreviewBitmap = remember(previewSource, rotationDegrees) {
        ImageEditProcessor.rotateBitmap(previewSource, rotationDegrees)
    }

    val targetOutWidth = remember(sourceBitmap, rotationDegrees, cropRect) {
        val rotW = if (rotationDegrees % 180 == 0) sourceBitmap.width else sourceBitmap.height
        (rotW * cropRect.width).roundToInt().coerceAtLeast(1)
    }
    val targetOutHeight = remember(sourceBitmap, rotationDegrees, cropRect) {
        val rotH = if (rotationDegrees % 180 == 0) sourceBitmap.height else sourceBitmap.width
        (rotH * cropRect.height).roundToInt().coerceAtLeast(1)
    }

    val hasChanges = rotationDegrees != 0 || !cropRect.isFullFrame()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1018))
            .testTag("photo_editor_root")
    ) {
        // Top Editor Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .testTag("editor_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.editor_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${targetOutWidth}×${targetOutHeight} px • ${rotationDegrees}°",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("editor_dimensions_label")
                )
            }

            TextButton(
                onClick = {
                    rotationDegrees = 0
                    selectedAspectRatio = CropAspectRatio.FREE
                    cropRect = NormalizedCropRect()
                },
                enabled = hasChanges && !isSaving,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("editor_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = if (hasChanges) LuminaSkyAccent else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset",
                    color = if (hasChanges) LuminaSkyAccent else Color.White.copy(alpha = 0.35f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Interactive Crop & Preview Canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val hitRadiusPx = with(density) { 36.dp.toPx() }

            val bmpW = rotatedPreviewBitmap.width.toFloat().coerceAtLeast(1f)
            val bmpH = rotatedPreviewBitmap.height.toFloat().coerceAtLeast(1f)
            val scale = min(containerWidthPx / bmpW, containerHeightPx / bmpH).coerceAtLeast(0.01f)
            val fittedWidthPx = bmpW * scale
            val fittedHeightPx = bmpH * scale

            val fittedWidthDp = with(density) { fittedWidthPx.toDp() }
            val fittedHeightDp = with(density) { fittedHeightPx.toDp() }

            Box(
                modifier = Modifier
                    .size(width = fittedWidthDp, height = fittedHeightDp)
                    .testTag("editor_crop_canvas")
            ) {
                Image(
                    bitmap = rotatedPreviewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.editor_title),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            rotatedPreviewBitmap.width,
                            rotatedPreviewBitmap.height,
                            selectedAspectRatio
                        ) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val cLeft = cropRect.left * fittedWidthPx
                                    val cTop = cropRect.top * fittedHeightPx
                                    val cRight = cropRect.right * fittedWidthPx
                                    val cBottom = cropRect.bottom * fittedHeightPx

                                    val dTopLeft = hypot(startOffset.x - cLeft, startOffset.y - cTop)
                                    val dTopRight = hypot(startOffset.x - cRight, startOffset.y - cTop)
                                    val dBottomLeft = hypot(startOffset.x - cLeft, startOffset.y - cBottom)
                                    val dBottomRight = hypot(startOffset.x - cRight, startOffset.y - cBottom)

                                    val nearestCorner = listOf(
                                        CropDragHandle.TOP_LEFT to dTopLeft,
                                        CropDragHandle.TOP_RIGHT to dTopRight,
                                        CropDragHandle.BOTTOM_LEFT to dBottomLeft,
                                        CropDragHandle.BOTTOM_RIGHT to dBottomRight
                                    ).minByOrNull { it.second }

                                    activeHandle = when {
                                        nearestCorner != null && nearestCorner.second <= hitRadiusPx -> nearestCorner.first
                                        startOffset.x in cLeft..cRight && startOffset.y in cTop..cBottom -> CropDragHandle.MOVE
                                        else -> CropDragHandle.NONE
                                    }
                                },
                                onDragEnd = { activeHandle = CropDragHandle.NONE },
                                onDragCancel = { activeHandle = CropDragHandle.NONE },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (activeHandle == CropDragHandle.NONE) return@detectDragGestures

                                    val dx = dragAmount.x / fittedWidthPx
                                    val dy = dragAmount.y / fittedHeightPx
                                    val minFrac = 0.12f

                                    cropRect = updateCropRectForDrag(
                                        current = cropRect,
                                        handle = activeHandle,
                                        dx = dx,
                                        dy = dy,
                                        minSize = minFrac,
                                        targetAspectRatio = selectedAspectRatio.ratio,
                                        imageAspectRatio = bmpW / bmpH
                                    )
                                }
                            )
                        }
                ) {
                    val cLeft = cropRect.left * size.width
                    val cTop = cropRect.top * size.height
                    val cRight = cropRect.right * size.width
                    val cBottom = cropRect.bottom * size.height
                    val cropBox = Rect(cLeft, cTop, cRight, cBottom)

                    // Dim outside region using Path.combine Difference
                    val outerPath = Path().apply { addRect(Rect(Offset.Zero, size)) }
                    val innerPath = Path().apply { addRect(cropBox) }
                    val scrimPath = Path.combine(PathOperation.Difference, outerPath, innerPath)
                    drawPath(path = scrimPath, color = Color.Black.copy(alpha = 0.58f))

                    // Crop rectangle border
                    drawRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(cLeft, cTop),
                        size = Size(cropBox.width, cropBox.height),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Rule-of-thirds interior lines
                    val thirdW = cropBox.width / 3f
                    val thirdH = cropBox.height / 3f
                    for (i in 1..2) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.38f),
                            start = Offset(cLeft + thirdW * i, cTop),
                            end = Offset(cLeft + thirdW * i, cBottom),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.38f),
                            start = Offset(cLeft, cTop + thirdH * i),
                            end = Offset(cRight, cTop + thirdH * i),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Corner L-brackets
                    val bracketLen = min(20.dp.toPx(), min(cropBox.width, cropBox.height) * 0.35f)
                    val bracketStroke = 3.5.dp.toPx()
                    val bracketColor = LuminaSkyAccent

                    // Top-Left
                    drawLine(bracketColor, Offset(cLeft, cTop), Offset(cLeft + bracketLen, cTop), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(cLeft, cTop), Offset(cLeft, cTop + bracketLen), bracketStroke, StrokeCap.Round)
                    // Top-Right
                    drawLine(bracketColor, Offset(cRight, cTop), Offset(cRight - bracketLen, cTop), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(cRight, cTop), Offset(cRight, cTop + bracketLen), bracketStroke, StrokeCap.Round)
                    // Bottom-Left
                    drawLine(bracketColor, Offset(cLeft, cBottom), Offset(cLeft + bracketLen, cBottom), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(cLeft, cBottom), Offset(cLeft, cBottom - bracketLen), bracketStroke, StrokeCap.Round)
                    // Bottom-Right
                    drawLine(bracketColor, Offset(cRight, cBottom), Offset(cRight - bracketLen, cBottom), bracketStroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(cRight, cBottom), Offset(cRight, cBottom - bracketLen), bracketStroke, StrokeCap.Round)
                }
            }
        }

        // Bottom Studio Controls Sheet
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF171C28)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Aspect Ratio Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropAspectRatio.entries.forEach { aspect ->
                        val isSelected = selectedAspectRatio == aspect
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedAspectRatio = aspect
                                cropRect = ImageEditProcessor.computeCenteredCropRect(
                                    imageWidth = rotatedPreviewBitmap.width,
                                    imageHeight = rotatedPreviewBitmap.height,
                                    aspectRatio = aspect
                                )
                            },
                            label = {
                                Text(
                                    text = aspect.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White.copy(alpha = 0.85f),
                                selectedContainerColor = LuminaSkyAccent,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("editor_aspect_${aspect.name.lowercase()}")
                        )
                    }
                }

                // Rotate & Save Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 270) % 360
                            cropRect = rotateCropRectLeft(cropRect)
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("editor_rotate_left_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = stringResource(R.string.action_rotate_left),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90) % 360
                            cropRect = rotateCropRectRight(cropRect)
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("editor_rotate_right_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.action_rotate_right),
                            tint = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "${rotationDegrees}°",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("editor_rotation_angle")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = { onSave(rotationDegrees, cropRect, true) },
                        enabled = !isSaving && hasChanges,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("editor_save_copy_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_save_copy),
                            color = if (hasChanges) Color.White else Color.White.copy(alpha = 0.35f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onSave(rotationDegrees, cropRect, false) },
                        enabled = !isSaving && hasChanges,
                        colors = ButtonDefaults.buttonColors(containerColor = LuminaSkyAccent),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("editor_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_save_edit),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rotateCropRectRight(rect: NormalizedCropRect): NormalizedCropRect {
    return NormalizedCropRect(
        left = 1f - rect.bottom,
        top = rect.left,
        right = 1f - rect.top,
        bottom = rect.right
    ).normalizedClamped()
}

private fun rotateCropRectLeft(rect: NormalizedCropRect): NormalizedCropRect {
    return NormalizedCropRect(
        left = rect.top,
        top = 1f - rect.right,
        right = rect.bottom,
        bottom = 1f - rect.left
    ).normalizedClamped()
}

private fun updateCropRectForDrag(
    current: NormalizedCropRect,
    handle: CropDragHandle,
    dx: Float,
    dy: Float,
    minSize: Float,
    targetAspectRatio: Float?,
    imageAspectRatio: Float
): NormalizedCropRect {
    if (handle == CropDragHandle.MOVE) {
        val w = current.width
        val h = current.height
        val newLeft = (current.left + dx).coerceIn(0f, 1f - w)
        val newTop = (current.top + dy).coerceIn(0f, 1f - h)
        return NormalizedCropRect(newLeft, newTop, newLeft + w, newTop + h)
    }

    var l = current.left
    var t = current.top
    var r = current.right
    var b = current.bottom

    when (handle) {
        CropDragHandle.TOP_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        CropDragHandle.TOP_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        CropDragHandle.BOTTOM_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
        CropDragHandle.BOTTOM_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
        else -> Unit
    }

    if (targetAspectRatio != null && imageAspectRatio > 0f) {
        // Normalized width / height ratio = targetAspectRatio / imageAspectRatio
        val normRatio = (targetAspectRatio / imageAspectRatio).coerceIn(0.1f, 10f)
        val rawW = (r - l).coerceAtLeast(minSize)
        val rawH = (b - t).coerceAtLeast(minSize)
        val (adjW, adjH) = if (abs(dx) >= abs(dy)) {
            val hFromW = (rawW / normRatio).coerceAtLeast(minSize)
            rawW to hFromW
        } else {
            val wFromH = (rawH * normRatio).coerceAtLeast(minSize)
            wFromH to rawH
        }

        when (handle) {
            CropDragHandle.TOP_LEFT -> {
                val maxW = r
                val maxH = b
                val scaleDown = min(1f, min(maxW / adjW, maxH / adjH))
                l = (r - adjW * scaleDown).coerceIn(0f, r - minSize)
                t = (b - adjH * scaleDown).coerceIn(0f, b - minSize)
            }
            CropDragHandle.TOP_RIGHT -> {
                val maxW = 1f - l
                val maxH = b
                val scaleDown = min(1f, min(maxW / adjW, maxH / adjH))
                r = (l + adjW * scaleDown).coerceIn(l + minSize, 1f)
                t = (b - adjH * scaleDown).coerceIn(0f, b - minSize)
            }
            CropDragHandle.BOTTOM_LEFT -> {
                val maxW = r
                val maxH = 1f - t
                val scaleDown = min(1f, min(maxW / adjW, maxH / adjH))
                l = (r - adjW * scaleDown).coerceIn(0f, r - minSize)
                b = (t + adjH * scaleDown).coerceIn(t + minSize, 1f)
            }
            CropDragHandle.BOTTOM_RIGHT -> {
                val maxW = 1f - l
                val maxH = 1f - t
                val scaleDown = min(1f, min(maxW / adjW, maxH / adjH))
                r = (l + adjW * scaleDown).coerceIn(l + minSize, 1f)
                b = (t + adjH * scaleDown).coerceIn(t + minSize, 1f)
            }
            else -> Unit
        }
    }

    return NormalizedCropRect(l, t, r, b).normalizedClamped(minSize)
}
