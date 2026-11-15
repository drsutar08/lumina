package com.example.data.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.BuildConfig
import com.example.R
import com.ironsource.mediationsdk.ads.nativead.LevelPlayMediaView
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAdListener
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.logger.IronSourceError
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

enum class SdkInitState {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED
}

enum class AdLoadState {
    IDLE,
    LOADING,
    READY,
    SHOWING,
    FAILED
}

/**
 * Singleton manager for the Unity LevelPlay mediation SDK and the lifecycle of
 * banner, interstitial, rewarded, and native ad formats.
 */
object LevelPlayManager {

    private const val TAG = "LevelPlayManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val appKey: String = BuildConfig.LEVELPLAY_APP_KEY
    private val bannerUnitId: String = BuildConfig.LEVELPLAY_BANNER_AD_UNIT_ID
    private val nativePlacementName: String = BuildConfig.LEVELPLAY_NATIVE_PLACEMENT_NAME
    private val rewardedUnitId: String = BuildConfig.LEVELPLAY_REWARDED_AD_UNIT_ID
    private val interstitialUnitId: String = BuildConfig.LEVELPLAY_INTERSTITIAL_AD_UNIT_ID

    val isSdkConfigured: Boolean = AdPolicyRules.isConfiguredCredential(appKey)
    val isBannerConfigured: Boolean = isSdkConfigured && AdPolicyRules.isConfiguredCredential(bannerUnitId)
    val isNativeConfigured: Boolean = isSdkConfigured && AdPolicyRules.isConfiguredCredential(nativePlacementName)
    val isRewardedConfigured: Boolean = isSdkConfigured && AdPolicyRules.isConfiguredCredential(rewardedUnitId)
    val isInterstitialConfigured: Boolean = isSdkConfigured && AdPolicyRules.isConfiguredCredential(interstitialUnitId)

    private val _sdkInitState = MutableStateFlow(SdkInitState.UNINITIALIZED)
    val sdkInitState: StateFlow<SdkInitState> = _sdkInitState.asStateFlow()

    private val _isSdkInitialized = MutableStateFlow(false)
    val isSdkInitialized: StateFlow<Boolean> = _isSdkInitialized.asStateFlow()

    private val _bannerState = MutableStateFlow(AdLoadState.IDLE)
    val bannerState: StateFlow<AdLoadState> = _bannerState.asStateFlow()

    private val _isBannerLoaded = MutableStateFlow(false)
    val isBannerLoaded: StateFlow<Boolean> = _isBannerLoaded.asStateFlow()

    private val _isNativeLoaded = MutableStateFlow(false)
    val isNativeLoaded: StateFlow<Boolean> = _isNativeLoaded.asStateFlow()

    private val _rewardedState = MutableStateFlow(AdLoadState.IDLE)
    val rewardedState: StateFlow<AdLoadState> = _rewardedState.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _interstitialState = MutableStateFlow(AdLoadState.IDLE)
    val interstitialState: StateFlow<AdLoadState> = _interstitialState.asStateFlow()

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    private val _nativeAdLayout = MutableStateFlow<NativeAdLayout?>(null)
    val nativeAdLayout: StateFlow<NativeAdLayout?> = _nativeAdLayout.asStateFlow()

    @Volatile
    private var isInitializing = false

    private var bannerAdView: LevelPlayBannerAdView? = null
    private var loadedNativeAd: LevelPlayNativeAd? = null
    private var rewardedAd: LevelPlayRewardedAd? = null
    private var interstitialAd: LevelPlayInterstitialAd? = null

    private var rewardedRetryAttempt = 0
    private var interstitialRetryAttempt = 0
    private var pendingRewardEarnedCallback: (() -> Unit)? = null
    private var pendingInterstitialShownCallback: (() -> Unit)? = null
    private var pendingInterstitialClosedCallback: (() -> Unit)? = null

    fun applyPrivacyConsent(consentState: AdConsentState) {
        if (consentState == AdConsentState.UNKNOWN) return
        val hasPersonalizedConsent = consentState == AdConsentState.PERSONALIZED
        try {
            LevelPlay.setConsent(hasPersonalizedConsent)
            LevelPlay.setMetaData("do_not_sell", if (hasPersonalizedConsent) "false" else "true")
            LevelPlay.setMetaData("is_child_directed", "false")
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to apply LevelPlay privacy flags: ${t.message}")
        }
    }

    /**
     * Initializes the Unity LevelPlay SDK only when the user has provided consent
     * and Ad-Free mode is disabled.
     */
    fun initializeIfEligible(
        context: Context,
        consentState: AdConsentState,
        isAdFreeEnabled: Boolean,
        activity: Activity? = null
    ) {
        if (!AdPolicyRules.shouldShowAds(consentState, isAdFreeEnabled)) {
            if (isAdFreeEnabled) {
                destroyBannerAndNative()
            }
            return
        }

        applyPrivacyConsent(consentState)
        init(context = context, activity = activity)
    }

    /**
     * Initializes the Unity LevelPlay SDK using [BuildConfig.LEVELPLAY_APP_KEY]
     * and preloads configured ad formats once ready.
     */
    fun init(
        context: Context,
        sdkAppKey: String = BuildConfig.LEVELPLAY_APP_KEY,
        activity: Activity? = null
    ) {
        if (!AdPolicyRules.isConfiguredCredential(sdkAppKey)) {
            return
        }

        if (_isSdkInitialized.value) {
            _sdkInitState.value = SdkInitState.INITIALIZED
            preloadEligibleFormats(activity)
            return
        }

        if (isInitializing) {
            return
        }

        val appContext = context.applicationContext
        val activityRef = activity?.let { WeakReference(it) }
        isInitializing = true
        _sdkInitState.value = SdkInitState.INITIALIZING

        try {
            val initRequest = LevelPlayInitRequest.Builder(sdkAppKey).build()
            LevelPlay.init(appContext, initRequest, object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    isInitializing = false
                    _sdkInitState.value = SdkInitState.INITIALIZED
                    _isSdkInitialized.value = true
                    preloadEligibleFormats(activityRef?.get())
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    isInitializing = false
                    _sdkInitState.value = SdkInitState.FAILED
                    _isSdkInitialized.value = false
                    Log.e(TAG, "LevelPlay init failed: ${error.errorMessage}")
                }
            })
        } catch (t: Throwable) {
            isInitializing = false
            _sdkInitState.value = SdkInitState.FAILED
            _isSdkInitialized.value = false
            Log.e(TAG, "LevelPlay SDK initialization failed: ${t.message}")
        }
    }

    private fun preloadEligibleFormats(activity: Activity?) {
        if (activity != null && isNativeConfigured && _nativeAdLayout.value == null) {
            loadNativeAd(activity)
        }
        if (isRewardedConfigured) {
            loadRewardedAd()
        }
        if (isInterstitialConfigured) {
            loadInterstitial()
        }
    }

    // --- Banner Ad Lifecycle ---

    /**
     * Creates (if needed) and loads a [LevelPlayBannerAdView] while tracking
     * banner lifecycle callbacks in [bannerState] and [isBannerLoaded].
     */
    fun loadBanner(context: Context): LevelPlayBannerAdView? {
        if (!isBannerConfigured || !_isSdkInitialized.value) return null

        bannerAdView?.let { existing ->
            if (!_isBannerLoaded.value && _bannerState.value != AdLoadState.LOADING) {
                _bannerState.value = AdLoadState.LOADING
                existing.loadAd()
            }
            return existing
        }

        val config = LevelPlayBannerAdView.Config.Builder()
            .setAdSize(LevelPlayAdSize.BANNER)
            .build()

        val adView = LevelPlayBannerAdView(context, bannerUnitId, config)
        adView.bannerListener = object : LevelPlayBannerAdViewListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                _bannerState.value = AdLoadState.READY
                _isBannerLoaded.value = true
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _bannerState.value = AdLoadState.FAILED
                _isBannerLoaded.value = false
                Log.w(TAG, "Banner failed to load: ${error.errorMessage}")
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                _bannerState.value = AdLoadState.SHOWING
            }

            override fun onAdDisplayFailed(adInfo: LevelPlayAdInfo, error: LevelPlayAdError) {
                _bannerState.value = AdLoadState.FAILED
                _isBannerLoaded.value = false
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

            override fun onAdExpanded(adInfo: LevelPlayAdInfo) {
                _bannerState.value = AdLoadState.SHOWING
            }

            override fun onAdCollapsed(adInfo: LevelPlayAdInfo) {
                if (_isBannerLoaded.value) {
                    _bannerState.value = AdLoadState.READY
                }
            }

            override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) {}
        }

        _bannerState.value = AdLoadState.LOADING
        adView.loadAd()
        bannerAdView = adView
        return adView
    }

    fun getOrCreateBannerView(context: Context): LevelPlayBannerAdView? = loadBanner(context)

    fun destroyBanner() {
        bannerAdView?.let { adView ->
            (adView.parent as? ViewGroup)?.removeView(adView)
            adView.destroy()
        }
        bannerAdView = null
        _bannerState.value = AdLoadState.IDLE
        _isBannerLoaded.value = false
    }

    // --- Native Ad Lifecycle ---

    fun loadNativeAd(activity: Activity) {
        if (!isNativeConfigured || !_isSdkInitialized.value) return

        val nativeAd = LevelPlayNativeAd.Builder()
            .withActivity(activity)
            .withPlacementName(nativePlacementName)
            .withListener(object : LevelPlayNativeAdListener {
                override fun onAdLoaded(levelPlayNativeAd: LevelPlayNativeAd?, adInfo: AdInfo?) {
                    if (levelPlayNativeAd == null) {
                        _isNativeLoaded.value = false
                        return
                    }
                    loadedNativeAd?.destroyAd()
                    loadedNativeAd = levelPlayNativeAd

                    val layout = LayoutInflater.from(activity)
                        .inflate(R.layout.lumina_native_ad_card, null) as NativeAdLayout

                    val titleView = layout.findViewById<TextView>(R.id.native_ad_title)
                    val advertiserView = layout.findViewById<TextView>(R.id.native_ad_advertiser)
                    val bodyView = layout.findViewById<TextView>(R.id.native_ad_body)
                    val iconView = layout.findViewById<ImageView>(R.id.native_ad_icon)
                    val mediaView = layout.findViewById<LevelPlayMediaView>(R.id.native_ad_media_view)
                    val ctaView = layout.findViewById<Button>(R.id.native_ad_cta)

                    levelPlayNativeAd.title?.let {
                        titleView.text = it
                        layout.setTitleView(titleView)
                    }
                    levelPlayNativeAd.advertiser?.let {
                        advertiserView.text = it
                        layout.setAdvertiserView(advertiserView)
                    }
                    levelPlayNativeAd.body?.let {
                        bodyView.text = it
                        layout.setBodyView(bodyView)
                    }
                    levelPlayNativeAd.icon?.drawable?.let {
                        iconView.setImageDrawable(it)
                        layout.setIconView(iconView)
                    }
                    levelPlayNativeAd.callToAction?.let {
                        ctaView.text = it
                        layout.setCallToActionView(ctaView)
                    }
                    layout.setMediaView(mediaView)
                    layout.registerNativeAdViews(levelPlayNativeAd)

                    _nativeAdLayout.value = layout
                    _isNativeLoaded.value = true
                }

                override fun onAdLoadFailed(levelPlayNativeAd: LevelPlayNativeAd?, error: IronSourceError?) {
                    _isNativeLoaded.value = false
                    Log.w(TAG, "Native ad load failed: ${error?.errorMessage}")
                }

                override fun onAdClicked(levelPlayNativeAd: LevelPlayNativeAd?, adInfo: AdInfo?) {}
                override fun onAdImpression(levelPlayNativeAd: LevelPlayNativeAd?, adInfo: AdInfo?) {}
            })
            .build()

        nativeAd.loadAd()
    }

    fun destroyNativeAd() {
        loadedNativeAd?.destroyAd()
        loadedNativeAd = null
        _nativeAdLayout.value = null
        _isNativeLoaded.value = false
    }

    // --- Rewarded Ad Lifecycle ---

    /**
     * Loads the Unity LevelPlay rewarded ad and wires lifecycle callbacks to
     * update [rewardedState] and [isRewardedReady] with exponential-backoff retries.
     */
    fun loadRewardedAd() {
        if (!isRewardedConfigured || !_isSdkInitialized.value) return
        rewardedAd?.let { existing ->
            if (existing.isAdReady()) {
                _rewardedState.value = AdLoadState.READY
                _isRewardedReady.value = true
            } else if (_rewardedState.value != AdLoadState.LOADING) {
                _rewardedState.value = AdLoadState.LOADING
                _isRewardedReady.value = false
                existing.loadAd()
            }
            return
        }

        val ad = LevelPlayRewardedAd(rewardedUnitId)
        ad.setListener(object : LevelPlayRewardedAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                rewardedRetryAttempt = 0
                _rewardedState.value = AdLoadState.READY
                _isRewardedReady.value = true
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _rewardedState.value = AdLoadState.FAILED
                _isRewardedReady.value = false
                rewardedRetryAttempt++
                val delayMs = TimeUnit.SECONDS.toMillis(
                    2.0.pow(min(6, rewardedRetryAttempt).toDouble()).toLong()
                )
                mainHandler.postDelayed({
                    if (rewardedAd === ad) {
                        _rewardedState.value = AdLoadState.LOADING
                        ad.loadAd()
                    }
                }, delayMs)
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                _rewardedState.value = AdLoadState.SHOWING
                _isRewardedReady.value = false
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                _rewardedState.value = AdLoadState.LOADING
                _isRewardedReady.value = false
                pendingRewardEarnedCallback = null
                ad.loadAd()
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                _rewardedState.value = AdLoadState.LOADING
                _isRewardedReady.value = false
                ad.loadAd()
            }

            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                pendingRewardEarnedCallback?.invoke()
                pendingRewardEarnedCallback = null
            }
        })
        _rewardedState.value = AdLoadState.LOADING
        ad.loadAd()
        rewardedAd = ad
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        if (!isRewardedConfigured) {
            onUnavailable("Configure Unity LevelPlay keys in AI Studio Secrets to watch rewarded ads.")
            return
        }
        if (!_isSdkInitialized.value) {
            onUnavailable("Unity LevelPlay is initializing. Please try again shortly.")
            return
        }
        val ad = rewardedAd
        if (ad != null && ad.isAdReady()) {
            pendingRewardEarnedCallback = onRewardEarned
            ad.showAd(activity)
        } else {
            loadRewardedAd()
            onUnavailable("Rewarded video is still loading. Try again in a moment.")
        }
    }

    fun destroyRewardedAd() {
        rewardedAd = null
        rewardedRetryAttempt = 0
        pendingRewardEarnedCallback = null
        _rewardedState.value = AdLoadState.IDLE
        _isRewardedReady.value = false
    }

    // --- Interstitial Ad Lifecycle ---

    /**
     * Loads the Unity LevelPlay interstitial ad and wires lifecycle callbacks to
     * update [interstitialState] and [isInterstitialReady] with exponential-backoff retries.
     */
    fun loadInterstitial() {
        if (!isInterstitialConfigured || !_isSdkInitialized.value) return
        interstitialAd?.let { existing ->
            if (existing.isAdReady()) {
                _interstitialState.value = AdLoadState.READY
                _isInterstitialReady.value = true
            } else if (_interstitialState.value != AdLoadState.LOADING) {
                _interstitialState.value = AdLoadState.LOADING
                _isInterstitialReady.value = false
                existing.loadAd()
            }
            return
        }

        val ad = LevelPlayInterstitialAd(interstitialUnitId)
        ad.setListener(object : LevelPlayInterstitialAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                interstitialRetryAttempt = 0
                _interstitialState.value = AdLoadState.READY
                _isInterstitialReady.value = true
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _interstitialState.value = AdLoadState.FAILED
                _isInterstitialReady.value = false
                interstitialRetryAttempt++
                val delayMs = TimeUnit.SECONDS.toMillis(
                    2.0.pow(min(6, interstitialRetryAttempt).toDouble()).toLong()
                )
                mainHandler.postDelayed({
                    if (interstitialAd === ad) {
                        _interstitialState.value = AdLoadState.LOADING
                        ad.loadAd()
                    }
                }, delayMs)
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                _interstitialState.value = AdLoadState.SHOWING
                _isInterstitialReady.value = false
                pendingInterstitialShownCallback?.invoke()
                pendingInterstitialShownCallback = null
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                _interstitialState.value = AdLoadState.LOADING
                _isInterstitialReady.value = false
                pendingInterstitialShownCallback = null
                pendingInterstitialClosedCallback?.invoke()
                pendingInterstitialClosedCallback = null
                ad.loadAd()
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                _interstitialState.value = AdLoadState.LOADING
                _isInterstitialReady.value = false
                pendingInterstitialClosedCallback?.invoke()
                pendingInterstitialClosedCallback = null
                ad.loadAd()
            }
        })
        _interstitialState.value = AdLoadState.LOADING
        ad.loadAd()
        interstitialAd = ad
    }

    fun showInterstitialIfReady(
        activity: Activity,
        onShown: () -> Unit = {},
        onClosed: () -> Unit = {}
    ): Boolean {
        if (!isInterstitialConfigured || !_isSdkInitialized.value) return false
        val ad = interstitialAd
        return if (ad != null && ad.isAdReady()) {
            pendingInterstitialShownCallback = onShown
            pendingInterstitialClosedCallback = onClosed
            ad.showAd(activity)
            true
        } else {
            loadInterstitial()
            false
        }
    }

    fun destroyInterstitial() {
        interstitialAd = null
        interstitialRetryAttempt = 0
        pendingInterstitialShownCallback = null
        pendingInterstitialClosedCallback = null
        _interstitialState.value = AdLoadState.IDLE
        _isInterstitialReady.value = false
    }

    // --- Shared Lifecycle Cleanup ---

    fun destroyBannerAndNative() {
        destroyBanner()
        destroyNativeAd()
    }

    fun destroyAll() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyBannerAndNative()
        destroyRewardedAd()
        destroyInterstitial()
    }
}
