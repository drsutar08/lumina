package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.CloudSyncIndicatorState

@Composable
fun BotanicalAccentCanvas(
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)

        val leafPath = Path().apply {
            moveTo(w * 0.85f, h * 0.1f)
            cubicTo(
                w * 0.45f, h * 0.05f,
                w * 0.15f, h * 0.35f,
                w * 0.32f, h * 0.72f
            )
            cubicTo(
                w * 0.48f, h * 0.92f,
                w * 0.88f, h * 0.85f,
                w * 0.95f, h * 0.45f
            )
            close()
        }
        drawPath(path = leafPath, color = lineColor, style = stroke)

        val veinPath = Path().apply {
            moveTo(w * 0.36f, h * 0.68f)
            quadraticTo(w * 0.62f, h * 0.45f, w * 0.88f, h * 0.22f)
        }
        drawPath(path = veinPath, color = lineColor.copy(alpha = 0.75f), style = stroke)

        drawLine(
            color = lineColor.copy(alpha = 0.65f),
            start = Offset(w * 0.56f, h * 0.48f),
            end = Offset(w * 0.42f, h * 0.32f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = lineColor.copy(alpha = 0.65f),
            start = Offset(w * 0.68f, h * 0.38f),
            end = Offset(w * 0.76f, h * 0.62f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SyncProgressRing(
    progress: Float,
    centerLabel: String,
    trackColor: Color,
    progressColor: Color,
    textColor: Color,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = (progress.coerceIn(0f, 1f)) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = centerLabel,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStateIllustrationView(
    @DrawableRes drawableRes: Int,
    imageContentDescription: String,
    title: String,
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    testTagPrefix: String = "empty_state",
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 20.dp)
            .testTag("${testTagPrefix}_container")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 260.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = imageContentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTagPrefix}_illustration")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("${testTagPrefix}_title")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LuminaSkyAccent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("${testTagPrefix}_action_button")
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CloudSyncIndicatorButton(
    syncState: CloudSyncIndicatorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onDarkSurface: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cloud_sync_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_icon_rotation"
    )

    val containerColor = when {
        onDarkSurface -> Color.White.copy(alpha = 0.18f)
        syncState == CloudSyncIndicatorState.SYNCING -> LuminaVioletPrimary.copy(alpha = 0.14f)
        syncState == CloudSyncIndicatorState.SYNCED -> Color(0xFFE5F6EB)
        else -> Color(0xFFFDECEC)
    }

    val contentTint = when {
        onDarkSurface -> when (syncState) {
            CloudSyncIndicatorState.SYNCING -> Color.White
            CloudSyncIndicatorState.SYNCED -> Color(0xFFB9F6CA)
            CloudSyncIndicatorState.OFFLINE -> Color(0xFFFFCDD2)
        }
        syncState == CloudSyncIndicatorState.SYNCING -> LuminaVioletPrimary
        syncState == CloudSyncIndicatorState.SYNCED -> Color(0xFF1B7F46)
        else -> Color(0xFFC62828)
    }

    val labelText = when (syncState) {
        CloudSyncIndicatorState.SYNCING -> stringResource(R.string.cloud_sync_status_syncing)
        CloudSyncIndicatorState.SYNCED -> stringResource(R.string.cloud_sync_status_synced)
        CloudSyncIndicatorState.OFFLINE -> stringResource(R.string.cloud_sync_status_offline)
    }

    val contentDesc = when (syncState) {
        CloudSyncIndicatorState.SYNCING -> stringResource(R.string.cloud_sync_cd_syncing)
        CloudSyncIndicatorState.SYNCED -> stringResource(R.string.cloud_sync_cd_synced)
        CloudSyncIndicatorState.OFFLINE -> stringResource(R.string.cloud_sync_cd_offline)
    }

    Surface(
        onClick = onClick,
        shape = if (showLabel) RoundedCornerShape(50) else CircleShape,
        color = containerColor,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("cloud_sync_indicator")
    ) {
        AnimatedContent(
            targetState = syncState,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "cloud_sync_indicator_state"
        ) { targetState ->
            val icon = when (targetState) {
                CloudSyncIndicatorState.SYNCING -> Icons.Default.Sync
                CloudSyncIndicatorState.SYNCED -> Icons.Default.CloudDone
                CloudSyncIndicatorState.OFFLINE -> Icons.Default.CloudOff
            }

            Row(
                modifier = Modifier.padding(
                    horizontal = if (showLabel) 12.dp else 10.dp,
                    vertical = 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = contentTint,
                    modifier = Modifier
                        .size(18.dp)
                        .then(
                            if (targetState == CloudSyncIndicatorState.SYNCING) {
                                Modifier.rotate(rotationAngle)
                            } else {
                                Modifier
                            }
                        )
                )
                if (showLabel) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentTint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TopAppBarSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_topbar_placeholder),
    focusRequester: FocusRequester? = null,
    testTagName: String = "topbar_search_textfield"
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LuminaVioletPrimary
                )
            } else {
                Icon(
                    imageVector = if (query.isNotBlank()) Icons.Default.AutoAwesome else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (query.isNotBlank()) LuminaVioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("${testTagName}_clear")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchSubmit()
                keyboardController?.hide()
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = LuminaVioletPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .testTag(testTagName)
    )
}
