package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.ads.AdConsentState
import com.example.data.ads.LevelPlayManager
import com.example.ui.theme.LuminaLavenderBackdrop
import com.example.ui.theme.LuminaOnSurface
import com.example.ui.theme.LuminaSkyAccent
import com.example.ui.theme.LuminaVioletPrimary
import com.example.ui.viewmodel.AdMonetizationUiState
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout
import com.unity3d.mediation.banner.LevelPlayBannerAdView

@Composable
fun LevelPlayBannerDock(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSdkInitialized by LevelPlayManager.isSdkInitialized.collectAsState()
    val bannerView: LevelPlayBannerAdView? = remember(
        visible,
        isSdkInitialized,
        LevelPlayManager.isBannerConfigured
    ) {
        if (visible && isSdkInitialized && LevelPlayManager.isBannerConfigured) {
            LevelPlayManager.loadBanner(context)
        } else {
            null
        }
    }

    AnimatedVisibility(
        visible = visible && bannerView != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .testTag("levelplay_banner_dock")
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bannerView != null) {
                    AndroidView(
                        factory = { viewContext ->
                            (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                            FrameLayout(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                addView(
                                    bannerView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun NativeAdFeedCard(
    nativeAdLayout: NativeAdLayout?,
    modifier: Modifier = Modifier
) {
    if (nativeAdLayout == null) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("gallery_native_ad_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, LuminaVioletPrimary.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LuminaLavenderBackdrop.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("native_ad_badge")
                ) {
                    Text(
                        text = stringResource(R.string.ad_badge_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LuminaVioletPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.ad_sponsored_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            AndroidView(
                factory = { viewContext ->
                    (nativeAdLayout.parent as? ViewGroup)?.removeView(nativeAdLayout)
                    FrameLayout(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        addView(
                            nativeAdLayout,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AdConsentDialog(
    onAcceptPersonalized: () -> Unit,
    onAcceptNonPersonalized: () -> Unit,
    onEnableAdFree: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("ad_consent_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LuminaLavenderBackdrop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = LuminaVioletPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.ad_consent_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LuminaOnSurface
                    )
                }

                Text(
                    text = stringResource(R.string.ad_consent_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onAcceptPersonalized,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminaVioletPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("consent_accept_personalized_button")
                ) {
                    Text(
                        text = stringResource(R.string.ad_consent_accept_personalized),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onAcceptNonPersonalized,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("consent_non_personalized_button")
                ) {
                    Text(
                        text = stringResource(R.string.ad_consent_non_personalized),
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = onEnableAdFree,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("consent_enable_ad_free_button")
                ) {
                    Text(
                        text = stringResource(R.string.ad_free_title),
                        color = LuminaSkyAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AdPreferencesCard(
    adState: AdMonetizationUiState,
    isSdkConfigured: Boolean,
    onToggleAdFree: (Boolean) -> Unit,
    onManagePrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ad_preferences_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = LuminaVioletPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ad_free_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.ad_free_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = adState.isAdFreeEnabled,
                    onCheckedChange = onToggleAdFree,
                    modifier = Modifier.testTag("ad_free_toggle_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val consentLabel = when (adState.consentState) {
                        AdConsentState.PERSONALIZED -> "Consent: Personalized (Unity LevelPlay)"
                        AdConsentState.NON_PERSONALIZED -> "Consent: Non-Personalized Ads"
                        AdConsentState.UNKNOWN -> "Consent: Not configured"
                    }
                    Text(
                        text = consentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LuminaOnSurface
                    )
                    Text(
                        text = if (isSdkConfigured) {
                            "Unity LevelPlay mediation ready"
                        } else {
                            stringResource(R.string.ad_sdk_awaiting_keys)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onManagePrivacy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("manage_ad_privacy_button")
                ) {
                    Text(
                        text = stringResource(R.string.ad_privacy_manage),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun RewardedStorageBoostBar(
    adState: AdMonetizationUiState,
    isRewardedConfigured: Boolean,
    isRewardedReady: Boolean,
    onWatchRewardedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rewarded_storage_boost_section"),
        shape = RoundedCornerShape(14.dp),
        color = LuminaLavenderBackdrop.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, LuminaVioletPrimary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (adState.canEarnMoreStorage) Icons.Default.CloudUpload else Icons.Default.Verified,
                    contentDescription = null,
                    tint = LuminaVioletPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (adState.canEarnMoreStorage) {
                            "Need more backup space?"
                        } else {
                            stringResource(R.string.ad_reward_max_reached)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = LuminaOnSurface
                    )
                    Text(
                        text = when {
                            !adState.canEarnMoreStorage -> "25 GB total cloud backup unlocked"
                            adState.bonusCloudGb > 0 -> "+${adState.bonusCloudGb} GB active • Watch another for +5 GB"
                            !isRewardedConfigured -> "Requires Unity LevelPlay rewarded ad key"
                            isRewardedReady -> stringResource(R.string.ad_reward_subtitle)
                            else -> "Loading Unity LevelPlay rewarded video..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (adState.canEarnMoreStorage) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onWatchRewardedClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminaVioletPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("unlock_storage_reward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ad_reward_unlock_cta),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
