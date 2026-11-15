package com.example.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.SlideshowTransition
import kotlin.math.roundToInt

fun slideshowTransitionSpec(
    transition: SlideshowTransition,
    direction: Int
): ContentTransform {
    val dir = if (direction >= 0) 1 else -1
    return when (transition) {
        SlideshowTransition.CROSSFADE -> {
            fadeIn(animationSpec = tween(550)) togetherWith
                fadeOut(animationSpec = tween(550))
        }
        SlideshowTransition.SLIDE -> {
            (slideInHorizontally(animationSpec = tween(420)) { width -> width * dir } +
                fadeIn(animationSpec = tween(300))) togetherWith
                (slideOutHorizontally(animationSpec = tween(420)) { width -> -width * dir } +
                    fadeOut(animationSpec = tween(300)))
        }
        SlideshowTransition.ZOOM -> {
            (fadeIn(animationSpec = tween(600)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(600))) togetherWith
                (fadeOut(animationSpec = tween(600)) +
                    scaleOut(targetScale = 1.08f, animationSpec = tween(600)))
        }
        SlideshowTransition.NONE -> {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }
}

@Composable
fun SlideshowPlayerBar(
    isPlaying: Boolean,
    currentIndex: Int,
    totalCount: Int,
    intervalSeconds: Int,
    transition: SlideshowTransition,
    progress: Float,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFullscreen: (() -> Unit)? = null,
    testTagPrefix: String = "slideshow",
    modifier: Modifier = Modifier
) {
    val safeIndex = if (totalCount > 0) (currentIndex.coerceIn(0, totalCount - 1) + 1) else 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_player_bar"),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isPlaying || progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(LuminaSkyAccent)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = totalCount > 1,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.action_previous_slide),
                        tint = Color.White.copy(alpha = if (totalCount > 1) 1f else 0.4f)
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    enabled = totalCount > 1,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) LuminaSkyAccent else Color.White.copy(alpha = 0.2f)
                        )
                        .testTag("${testTagPrefix}_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.action_pause_slideshow)
                        } else {
                            stringResource(R.string.action_start_slideshow)
                        },
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = totalCount > 1,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.action_next_slide),
                        tint = Color.White.copy(alpha = if (totalCount > 1) 1f else 0.4f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPlaying) {
                            "Slideshow • $safeIndex / $totalCount"
                        } else {
                            "Slide $safeIndex of $totalCount"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("${testTagPrefix}_counter_text")
                    )
                    Text(
                        text = "${intervalSeconds}s • ${transition.label}",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("${testTagPrefix}_config_summary")
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.action_slideshow_settings),
                        tint = Color.White
                    )
                }

                if (onOpenFullscreen != null) {
                    IconButton(
                        onClick = onOpenFullscreen,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("${testTagPrefix}_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.action_fullscreen_inspect),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlideshowSettingsDialog(
    intervalSeconds: Int,
    transition: SlideshowTransition,
    loopEnabled: Boolean,
    onIntervalSelected: (Int) -> Unit,
    onTransitionSelected: (SlideshowTransition) -> Unit,
    onLoopToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val presetIntervals = listOf(2, 4, 6, 10)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.slideshow_settings_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.slideshow_interval_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = LuminaSkyAccent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${intervalSeconds}s per photo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = LuminaSkyAccent,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("slideshow_interval_value")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetIntervals.forEach { preset ->
                            val selected = intervalSeconds == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onIntervalSelected(preset) },
                                label = {
                                    Text(
                                        text = "${preset}s",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuminaSkyAccent,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("interval_preset_${preset}s")
                            )
                        }
                    }

                    Slider(
                        value = intervalSeconds.toFloat(),
                        onValueChange = { onIntervalSelected(it.roundToInt()) },
                        valueRange = 2f..15f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = LuminaSkyAccent,
                            activeTrackColor = LuminaSkyAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slideshow_interval_slider")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.slideshow_transition_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    SlideshowTransition.entries.forEach { option ->
                        val isSelected = option == transition
                        Surface(
                            onClick = { onTransitionSelected(option) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("transition_option_${option.name.lowercase()}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                LuminaVioletPrimary.copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    LuminaVioletPrimary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) LuminaVioletPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = LuminaVioletPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLoopToggled(!loopEnabled) }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.slideshow_loop_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = loopEnabled,
                        onCheckedChange = onLoopToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LuminaSkyAccent
                        ),
                        modifier = Modifier.testTag("slideshow_loop_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = LuminaSkyAccent),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("slideshow_settings_done_button")
            ) {
                Text(stringResource(R.string.slideshow_done))
            }
        }
    )
}
