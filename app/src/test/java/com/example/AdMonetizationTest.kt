package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ads.AdConsentState
import com.example.data.ads.AdLoadState
import com.example.data.ads.AdPolicyRules
import com.example.data.ads.AdPreferencesRepository
import com.example.data.ads.LevelPlayManager
import com.example.data.ads.SdkInitState
import com.example.ui.components.AdConsentDialog
import com.example.ui.components.AdPreferencesCard
import com.example.ui.components.RewardedStorageBoostBar
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AdMonetizationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdMonetizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun policyRules_enforceConsentAdFreeAndInterstitialCooldown() {
        assertFalse(AdPolicyRules.shouldShowAds(AdConsentState.UNKNOWN, isAdFreeEnabled = false))
        assertTrue(AdPolicyRules.shouldShowAds(AdConsentState.PERSONALIZED, isAdFreeEnabled = false))
        assertTrue(AdPolicyRules.shouldShowAds(AdConsentState.NON_PERSONALIZED, isAdFreeEnabled = false))
        assertFalse(AdPolicyRules.shouldShowAds(AdConsentState.PERSONALIZED, isAdFreeEnabled = true))

        // First export is always skipped
        assertFalse(
            AdPolicyRules.canShowPostExportInterstitial(
                nowMs = 500_000L,
                isAdFreeEnabled = false,
                consentState = AdConsentState.PERSONALIZED,
                lastShownAtMs = 0L,
                completedExportsCount = 1
            )
        )
        // Second export with no prior interstitial is eligible
        assertTrue(
            AdPolicyRules.canShowPostExportInterstitial(
                nowMs = 500_000L,
                isAdFreeEnabled = false,
                consentState = AdConsentState.PERSONALIZED,
                lastShownAtMs = 0L,
                completedExportsCount = 2
            )
        )
        // Within 3-minute cooldown is blocked
        assertFalse(
            AdPolicyRules.canShowPostExportInterstitial(
                nowMs = 600_000L,
                isAdFreeEnabled = false,
                consentState = AdConsentState.PERSONALIZED,
                lastShownAtMs = 500_000L,
                completedExportsCount = 3
            )
        )
        // After 3-minute cooldown is eligible again
        assertTrue(
            AdPolicyRules.canShowPostExportInterstitial(
                nowMs = 700_000L,
                isAdFreeEnabled = false,
                consentState = AdConsentState.PERSONALIZED,
                lastShownAtMs = 500_000L,
                completedExportsCount = 3
            )
        )
        // Ad-Free mode blocks interstitial even past cooldown
        assertFalse(
            AdPolicyRules.canShowPostExportInterstitial(
                nowMs = 700_000L,
                isAdFreeEnabled = true,
                consentState = AdConsentState.PERSONALIZED,
                lastShownAtMs = 500_000L,
                completedExportsCount = 3
            )
        )

        // Placeholder vs configured LevelPlay credentials
        assertFalse(AdPolicyRules.isConfiguredCredential("MY_LEVELPLAY_APP_KEY"))
        assertFalse(AdPolicyRules.isConfiguredCredential("   "))
        assertTrue(AdPolicyRules.isConfiguredCredential("1a2b3c4d5e"))
    }

    @Test
    fun preferencesRepository_persistsConsentAndCapsRewardedCloudBonus() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        context.getSharedPreferences("lumina_ad_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo = AdPreferencesRepository(context)
        assertEquals(AdConsentState.UNKNOWN, repo.consentState.value)
        assertFalse(repo.isAdFreeEnabled.value)
        assertEquals(0L, repo.bonusCloudStorageBytes.value)

        repo.setConsentState(AdConsentState.PERSONALIZED)
        assertEquals(AdConsentState.PERSONALIZED, repo.consentState.value)

        val firstReward = repo.grantStorageReward()
        assertEquals(AdPolicyRules.REWARD_STORAGE_INCREMENT_BYTES, firstReward)
        val secondReward = repo.grantStorageReward()
        assertEquals(AdPolicyRules.MAX_BONUS_STORAGE_BYTES, secondReward)
        // Capped at +10 GB (total 25 GB with 15 GB base)
        val thirdReward = repo.grantStorageReward()
        assertEquals(AdPolicyRules.MAX_BONUS_STORAGE_BYTES, thirdReward)
    }

    @Test
    fun adConsentDialog_invokesPersonalizedNonPersonalizedAndAdFreeCallbacks() {
        var personalizedClicks = 0
        var nonPersonalizedClicks = 0
        var adFreeClicks = 0

        composeRule.setContent {
            MyApplicationTheme {
                AdConsentDialog(
                    onAcceptPersonalized = { personalizedClicks++ },
                    onAcceptNonPersonalized = { nonPersonalizedClicks++ },
                    onEnableAdFree = { adFreeClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag("ad_consent_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("consent_accept_personalized_button").performClick()
        composeRule.onNodeWithTag("consent_non_personalized_button").performClick()
        composeRule.onNodeWithTag("consent_enable_ad_free_button").performClick()

        assertEquals(1, personalizedClicks)
        assertEquals(1, nonPersonalizedClicks)
        assertEquals(1, adFreeClicks)
    }

    @Test
    fun adPreferencesAndRewardedStorageBoost_updateUiStateOnInteraction() {
        var adFreeState by mutableStateOf(false)
        var managePrivacyClicks = 0
        var watchRewardedClicks = 0

        composeRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    AdPreferencesCard(
                        adState = AdMonetizationUiState(
                            consentState = AdConsentState.PERSONALIZED,
                            isAdFreeEnabled = adFreeState,
                            bonusCloudStorageBytes = AdPolicyRules.REWARD_STORAGE_INCREMENT_BYTES
                        ),
                        isSdkConfigured = false,
                        onToggleAdFree = { adFreeState = it },
                        onManagePrivacy = { managePrivacyClicks++ }
                    )
                    RewardedStorageBoostBar(
                        adState = AdMonetizationUiState(
                            consentState = AdConsentState.PERSONALIZED,
                            isAdFreeEnabled = adFreeState,
                            bonusCloudStorageBytes = AdPolicyRules.REWARD_STORAGE_INCREMENT_BYTES
                        ),
                        isRewardedConfigured = true,
                        isRewardedReady = true,
                        onWatchRewardedClick = { watchRewardedClicks++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ad_free_toggle_switch").performClick()
        assertTrue(adFreeState)

        composeRule.onNodeWithTag("manage_ad_privacy_button").performClick()
        assertEquals(1, managePrivacyClicks)

        composeRule.onNodeWithText("+5 GB active • Watch another for +5 GB").assertIsDisplayed()
        composeRule.onNodeWithTag("unlock_storage_reward_button").performClick()
        assertEquals(1, watchRewardedClicks)
    }

    @Test
    fun levelPlayManagerSingleton_respectsConsentAndLifecycleCleanup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()

        // Singleton identity check
        assertTrue(LevelPlayManager === LevelPlayManager)

        // Placeholder secrets from .env.example remain unconfigured until added in Secrets panel
        assertFalse(LevelPlayManager.isSdkConfigured)
        assertFalse(LevelPlayManager.isBannerConfigured)
        assertFalse(LevelPlayManager.isRewardedConfigured)
        assertFalse(LevelPlayManager.isInterstitialConfigured)

        // Should not initialize SDK when consent is UNKNOWN or Ad-Free is enabled
        LevelPlayManager.initializeIfEligible(
            context = context,
            consentState = AdConsentState.UNKNOWN,
            isAdFreeEnabled = false
        )
        assertEquals(SdkInitState.UNINITIALIZED, LevelPlayManager.sdkInitState.value)
        assertFalse(LevelPlayManager.isSdkInitialized.value)

        LevelPlayManager.initializeIfEligible(
            context = context,
            consentState = AdConsentState.PERSONALIZED,
            isAdFreeEnabled = true
        )
        assertEquals(SdkInitState.UNINITIALIZED, LevelPlayManager.sdkInitState.value)
        assertFalse(LevelPlayManager.isSdkInitialized.value)
        assertEquals(null, LevelPlayManager.loadBanner(context))

        // Loading ad formats before SDK init stays safely IDLE
        LevelPlayManager.loadInterstitial()
        LevelPlayManager.loadRewardedAd()
        assertEquals(AdLoadState.IDLE, LevelPlayManager.bannerState.value)
        assertEquals(AdLoadState.IDLE, LevelPlayManager.interstitialState.value)
        assertEquals(AdLoadState.IDLE, LevelPlayManager.rewardedState.value)

        // Cleanup resets all format readiness states safely
        LevelPlayManager.destroyAll()
        assertEquals(AdLoadState.IDLE, LevelPlayManager.bannerState.value)
        assertEquals(AdLoadState.IDLE, LevelPlayManager.interstitialState.value)
        assertEquals(AdLoadState.IDLE, LevelPlayManager.rewardedState.value)
        assertFalse(LevelPlayManager.isBannerLoaded.value)
        assertFalse(LevelPlayManager.isRewardedReady.value)
        assertFalse(LevelPlayManager.isInterstitialReady.value)
    }
}
