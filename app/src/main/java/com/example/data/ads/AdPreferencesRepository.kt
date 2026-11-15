package com.example.data.ads

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdConsentState {
    UNKNOWN,
    PERSONALIZED,
    NON_PERSONALIZED
}

object AdPolicyRules {
    const val BASE_CLOUD_LIMIT_BYTES: Long = 15L * 1024 * 1024 * 1024
    const val REWARD_STORAGE_INCREMENT_BYTES: Long = 5L * 1024 * 1024 * 1024
    const val MAX_BONUS_STORAGE_BYTES: Long = 10L * 1024 * 1024 * 1024
    const val INTERSTITIAL_COOLDOWN_MS: Long = 180_000L

    fun shouldShowAds(
        consentState: AdConsentState,
        isAdFreeEnabled: Boolean
    ): Boolean {
        return !isAdFreeEnabled && consentState != AdConsentState.UNKNOWN
    }

    fun canShowPostExportInterstitial(
        nowMs: Long,
        isAdFreeEnabled: Boolean,
        consentState: AdConsentState,
        lastShownAtMs: Long,
        completedExportsCount: Int,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS
    ): Boolean {
        if (!shouldShowAds(consentState, isAdFreeEnabled)) return false
        // Grace skip on the very first export so onboarding feels frictionless
        if (completedExportsCount <= 1) return false
        if (lastShownAtMs <= 0L) return true
        return (nowMs - lastShownAtMs) >= cooldownMs
    }

    fun isConfiguredCredential(value: String, placeholderPrefix: String = "MY_LEVELPLAY_"): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() && !trimmed.startsWith(placeholderPrefix)
    }
}

class AdPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _consentState = MutableStateFlow(loadConsentState())
    val consentState: StateFlow<AdConsentState> = _consentState.asStateFlow()

    private val _isAdFreeEnabled = MutableStateFlow(prefs.getBoolean(KEY_AD_FREE, false))
    val isAdFreeEnabled: StateFlow<Boolean> = _isAdFreeEnabled.asStateFlow()

    private val _bonusCloudStorageBytes = MutableStateFlow(
        prefs.getLong(KEY_BONUS_STORAGE_BYTES, 0L)
            .coerceIn(0L, AdPolicyRules.MAX_BONUS_STORAGE_BYTES)
    )
    val bonusCloudStorageBytes: StateFlow<Long> = _bonusCloudStorageBytes.asStateFlow()

    private val _lastInterstitialShownAtMs = MutableStateFlow(
        prefs.getLong(KEY_LAST_INTERSTITIAL_MS, 0L)
    )
    val lastInterstitialShownAtMs: StateFlow<Long> = _lastInterstitialShownAtMs.asStateFlow()

    private val _completedExportsCount = MutableStateFlow(
        prefs.getInt(KEY_EXPORTS_COUNT, 0)
    )
    val completedExportsCount: StateFlow<Int> = _completedExportsCount.asStateFlow()

    private fun loadConsentState(): AdConsentState {
        val raw = prefs.getString(KEY_CONSENT_STATE, AdConsentState.UNKNOWN.name)
        return AdConsentState.entries.firstOrNull { it.name == raw } ?: AdConsentState.UNKNOWN
    }

    fun setConsentState(state: AdConsentState) {
        prefs.edit().putString(KEY_CONSENT_STATE, state.name).apply()
        _consentState.value = state
    }

    fun setAdFreeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_FREE, enabled).apply()
        _isAdFreeEnabled.value = enabled
    }

    fun grantStorageReward(): Long {
        val updated = (_bonusCloudStorageBytes.value + AdPolicyRules.REWARD_STORAGE_INCREMENT_BYTES)
            .coerceAtMost(AdPolicyRules.MAX_BONUS_STORAGE_BYTES)
        prefs.edit().putLong(KEY_BONUS_STORAGE_BYTES, updated).apply()
        _bonusCloudStorageBytes.value = updated
        return updated
    }

    fun recordExportAndCheckEligibility(nowMs: Long = System.currentTimeMillis()): Boolean {
        val nextCount = _completedExportsCount.value + 1
        prefs.edit().putInt(KEY_EXPORTS_COUNT, nextCount).apply()
        _completedExportsCount.value = nextCount

        return AdPolicyRules.canShowPostExportInterstitial(
            nowMs = nowMs,
            isAdFreeEnabled = _isAdFreeEnabled.value,
            consentState = _consentState.value,
            lastShownAtMs = _lastInterstitialShownAtMs.value,
            completedExportsCount = nextCount
        )
    }

    fun markInterstitialShown(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_INTERSTITIAL_MS, nowMs).apply()
        _lastInterstitialShownAtMs.value = nowMs
    }

    companion object {
        private const val PREFS_NAME = "lumina_ad_prefs"
        private const val KEY_CONSENT_STATE = "consent_state"
        private const val KEY_AD_FREE = "ad_free_enabled"
        private const val KEY_BONUS_STORAGE_BYTES = "bonus_cloud_storage_bytes"
        private const val KEY_LAST_INTERSTITIAL_MS = "last_interstitial_ms"
        private const val KEY_EXPORTS_COUNT = "completed_exports_count"
    }
}
