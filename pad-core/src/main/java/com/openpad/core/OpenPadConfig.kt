package com.openpad.core

/**
 * Configuration for the OpenPad SDK.
 *
 * Controls detection thresholds, scoring weights, retry limits, and timing.
 * Most integrators should use [Default] unless they need to tune sensitivity.
 *
 * ### Quick start
 * ```kotlin
 * OpenPad.initialize(context, OpenPadConfig(
 *     livenessThreshold = 0.65f,
 *     faceMatchThreshold = 0.75f
 * ))
 * ```
 *
 * ### Scoring weights
 * The final liveness score is a weighted sum of ML signals. The weights
 * should sum to approximately 1.0:
 * - [textureAnalysisWeight] — surface texture patterns (print/screen artifacts)
 * - [depthAnalysisWeight] — 3D depth map quality (flat = spoof)
 * - [depthGateWeight] — fast depth pre-filter
 *
 * @property livenessThreshold Minimum overall confidence to accept as live. Range [0.0, 1.0].
 * @property faceMatchThreshold Minimum similarity between checkpoint face captures. Below this = face swap. Range [0.0, 1.0].
 * @property faceDetectionConfidence Minimum confidence for face detection. Range [0.0, 1.0].
 * @property textureAnalysisWeight Scoring weight for texture analysis. Default 0.15.
 * @property depthGateWeight Scoring weight for the fast depth pre-filter. Default 0.20.
 * @property depthAnalysisWeight Scoring weight for full depth map analysis. Default 0.55.
 * @property depthGateMinScore Minimum depth gate score to run the full depth analysis. Lower = more permissive.
 * @property depthFlatnessMinScore Hard cutoff: faces flatter than this are rejected as spoof.
 * @property moireDetectionThreshold Moire score above this triggers the frequency gate. Range [0.0, 1.0].
 * @property screenPatternThreshold LBP screen pattern score above this triggers the frequency gate. Range [0.0, 1.0].
 * @property photometricMinScore Combined photometric score below this triggers spoof detection. Range [0.0, 1.0].
 * @property spoofAttemptPenalty Extra threshold added per consecutive failed attempt.
 * @property maxSpoofAttempts Maximum number of spoof retries before the session terminates with a spoof verdict. 0 = unlimited (not recommended). Default 2.
 * @property maxFramesPerSecond Maximum frame processing rate.
 * @property enableDebugOverlay If true, shows real-time debug metrics during the camera phase.
 * @property staticFrameThreshold Frame similarity above this flags a static image (printed photo). Range [0.9, 1.0].
 * @property minMotionVariance Head movement variance below this flags no involuntary micro-movements. Lower = stricter.
 * @property lowLightThreshold Face luminance below this activates low-light adaptation (relaxed thresholds). Range [0.0, 1.0].
 * @property lowLightRelaxation Amount subtracted from liveness threshold when low light is detected. Higher = more lenient in dim conditions.
 * @property screenReflectionMinConfidence Minimum detection confidence for the screen-reflection model to count. Range [0.0, 1.0].
 * @property screenReflectionMinSignals Minimum number of overlapping spoof-class detections (reflection, artifact, bezel, finger) to trigger spoof gate.
 * @property screenReflectionWeight Scoring weight for screen-reflection detection. Default 0.08.
 * @property sessionTimeoutMs Maximum total time (ms) for the entire verification session. If the user hasn't completed the challenge within this window the SDK delivers a verdict using whatever signals it has. 0 = no timeout. Default 8 000 ms (8 s).
 * @property challengeTimeoutMs Maximum time (ms) the user may spend in the "move closer" challenge before an automatic verdict is issued using accumulated signals. 0 = no timeout.
 * @property enablePreprocessing If true, apply classical preprocessing (gamma correction + CLAHE) to each frame before ML inference. Improves accuracy in low-light and uneven lighting.
 * @property preprocessingGammaTarget Target luminance for adaptive gamma correction. Set to 0 to disable gamma. Range [0.0, 1.0].
 * @property preprocessingClaheClipLimit CLAHE clip limit controlling local contrast amplification. Set to 0 to disable CLAHE. Typical range [1.0, 4.0].
 */
import android.content.Context

data class OpenPadConfig(
    val livenessThreshold: Float = 0.70f,
    val faceMatchThreshold: Float = 0.70f,
    val faceDetectionConfidence: Float = 0.55f,
    val textureAnalysisWeight: Float = 0.15f,
    val depthGateWeight: Float = 0.20f,
    val depthAnalysisWeight: Float = 0.55f,
    val depthGateMinScore: Float = 0.20f,
    val depthFlatnessMinScore: Float = 0.40f,
    val moireDetectionThreshold: Float = 0.60f,
    val screenPatternThreshold: Float = 0.70f,
    val photometricMinScore: Float = 0.30f,
    val spoofAttemptPenalty: Float = 0.08f,
    val maxSpoofAttempts: Int = 2,
    val maxFramesPerSecond: Int = 8,
    val enableDebugOverlay: Boolean = false,
    /** Enable ESPCN super-resolution on face regions during the closer challenge.
     *  The model's quality gate automatically discards enhancements that don't help. */
    val enableFrameEnhancement: Boolean = true,
    val staticFrameThreshold: Float = 0.997f,
    val minMotionVariance: Float = 0.1f,
    val lowLightThreshold: Float = 0.40f,
    val lowLightRelaxation: Float = 0.30f,
    val screenReflectionMinConfidence: Float = 0.50f,
    val screenReflectionMinSignals: Int = 2,
    val screenReflectionWeight: Float = 0.08f,
    val sessionTimeoutMs: Long = 10_000L,
    val challengeTimeoutMs: Long = 10_000L,
    val enablePreprocessing: Boolean = true,
    val preprocessingGammaTarget: Float = 0.45f,
    val preprocessingClaheClipLimit: Float = 2.0f,
) {
    companion object {

        /**
         * Auto-selects a config preset based on device hardware capabilities.
         * Low-end devices (<=4 cores or <=3 GB RAM) get [LowEndDevice],
         * everything else gets [Default]. Call during initialization:
         * ```kotlin
         * OpenPad.initialize(context, config = OpenPadConfig.forDevice(context))
         * ```
         */
        fun forDevice(context: Context): OpenPadConfig {
            return when (DeviceCapabilityDetector.detect(context)) {
                DeviceCapabilityDetector.Tier.LOW -> LowEndDevice
                DeviceCapabilityDetector.Tier.MID -> Default
                DeviceCapabilityDetector.Tier.HIGH -> Default
            }
        }

        /** Balanced security and usability for general-purpose apps. */
        val Default = OpenPadConfig()

        /**
         * Strict thresholds for high-value transactions: banking, payments,
         * identity verification. Raises liveness and face-match thresholds,
         * tightens depth and photometric gates. Accepts slightly higher
         * false-rejection rate in exchange for stronger spoof resistance.
         */
        val HighSecurity = OpenPadConfig(
            livenessThreshold = 0.78f,
            faceMatchThreshold = 0.78f,
            faceDetectionConfidence = 0.60f,
            depthGateMinScore = 0.25f,
            depthFlatnessMinScore = 0.45f,
            moireDetectionThreshold = 0.50f,
            screenPatternThreshold = 0.60f,
            photometricMinScore = 0.32f,
            spoofAttemptPenalty = 0.10f,
            staticFrameThreshold = 0.996f,
            minMotionVariance = 0.12f,
            lowLightThreshold = 0.35f,
            lowLightRelaxation = 0.22f,
            screenReflectionMinConfidence = 0.45f,
            screenReflectionMinSignals = 2,
            screenReflectionWeight = 0.10f
        )

        /**
         * Quick verification for low-risk flows: attendance, check-in,
         * casual access. Lowers liveness threshold so legitimate users
         * pass faster. Not suitable for financial or identity use cases.
         */
        val FastPass = OpenPadConfig(
            livenessThreshold = 0.55f,
            faceMatchThreshold = 0.60f,
            faceDetectionConfidence = 0.50f,
            depthFlatnessMinScore = 0.35f,
            photometricMinScore = 0.25f,
            spoofAttemptPenalty = 0.05f,
            maxFramesPerSecond = 12
        )

        /**
         * Financial-grade configuration for banking and payment apps.
         * Stricter than [HighSecurity] with a higher per-attempt penalty
         * and tighter photometric gate. Designed to meet regulatory
         * expectations for remote identity verification.
         */
        val Banking = OpenPadConfig(
            livenessThreshold = 0.80f,
            faceMatchThreshold = 0.80f,
            faceDetectionConfidence = 0.60f,
            depthGateMinScore = 0.25f,
            depthFlatnessMinScore = 0.45f,
            moireDetectionThreshold = 0.45f,
            screenPatternThreshold = 0.55f,
            photometricMinScore = 0.35f,
            spoofAttemptPenalty = 0.12f,
            staticFrameThreshold = 0.996f,
            minMotionVariance = 0.12f,
            lowLightThreshold = 0.35f,
            lowLightRelaxation = 0.22f,
            screenReflectionMinConfidence = 0.45f,
            screenReflectionMinSignals = 2,
            screenReflectionWeight = 0.12f
        )

        /**
         * Slightly relaxed thresholds for first-time user onboarding
         * where drop-off is costly. Reduces false rejections while
         * maintaining reasonable spoof protection. Pair with a
         * server-side re-verification for sensitive operations.
         */
        val Onboarding = OpenPadConfig(
            livenessThreshold = 0.60f,
            faceMatchThreshold = 0.65f,
            faceDetectionConfidence = 0.50f,
            depthFlatnessMinScore = 0.35f,
            photometricMinScore = 0.25f,
            spoofAttemptPenalty = 0.06f
        )

        /**
         * Tuned for fixed-mount kiosks and point-of-sale terminals with
         * controlled lighting and camera distance. Raises face detection
         * confidence (stable environment) and tightens depth gates
         * (consistent viewing angle).
         */
        val Kiosk = OpenPadConfig(
            livenessThreshold = 0.75f,
            faceMatchThreshold = 0.75f,
            faceDetectionConfidence = 0.65f,
            depthGateMinScore = 0.25f,
            depthFlatnessMinScore = 0.45f,
            photometricMinScore = 0.35f,
            maxFramesPerSecond = 10,
            lowLightThreshold = 0.25f,
            lowLightRelaxation = 0.10f,
            screenReflectionMinConfidence = 0.45f,
            screenReflectionMinSignals = 2,
            screenReflectionWeight = 0.10f,
            preprocessingClaheClipLimit = 1.5f
        )

        /**
         * Optimized for budget Android phones with limited CPU/GPU.
         * Disables ESPCN frame enhancement, reduces frame rate to 5 FPS,
         * and relaxes thresholds slightly to compensate for lower image
         * quality from cheaper cameras.
         */
        val LowEndDevice = OpenPadConfig(
            livenessThreshold = 0.65f,
            faceMatchThreshold = 0.65f,
            faceDetectionConfidence = 0.50f,
            depthFlatnessMinScore = 0.35f,
            photometricMinScore = 0.25f,
            maxFramesPerSecond = 5,
            enableFrameEnhancement = false,
            enablePreprocessing = false
        )

        /**
         * Development and integration testing preset. Enables the debug
         * overlay showing real-time ML scores and relaxes thresholds so
         * the pipeline reaches LIVE easily during testing. Never use
         * this in production.
         */
        val Development = OpenPadConfig(
            livenessThreshold = 0.40f,
            faceMatchThreshold = 0.50f,
            faceDetectionConfidence = 0.40f,
            depthFlatnessMinScore = 0.25f,
            photometricMinScore = 0.15f,
            spoofAttemptPenalty = 0.02f,
            enableDebugOverlay = true,
            staticFrameThreshold = 1.0f,
            minMotionVariance = 0.0f,
            lowLightThreshold = 0.50f,
            lowLightRelaxation = 0.35f,
            screenReflectionMinConfidence = 0.60f,
            screenReflectionMinSignals = 3,
            screenReflectionWeight = 0.05f,
            preprocessingClaheClipLimit = 3.0f
        )

        /**
         * Maximum processing speed for high-throughput scenarios: queues,
         * turnstiles, batch processing. Runs at 15 FPS with relaxed face
         * matching. Security is moderate -- suitable when combined with
         * additional server-side verification.
         */
        val HighThroughput = OpenPadConfig(
            livenessThreshold = 0.60f,
            faceMatchThreshold = 0.60f,
            faceDetectionConfidence = 0.50f,
            depthFlatnessMinScore = 0.35f,
            photometricMinScore = 0.25f,
            spoofAttemptPenalty = 0.05f,
            maxFramesPerSecond = 15,
            enableFrameEnhancement = false,
            enablePreprocessing = false
        )

        /**
         * All detection gates at their strictest settings. Highest
         * liveness threshold (0.90), tightest depth and frequency gates.
         * Expect a higher false-rejection rate -- only use when
         * security is the absolute priority over user experience.
         */
        val MaxAccuracy = OpenPadConfig(
            livenessThreshold = 0.82f,
            faceMatchThreshold = 0.82f,
            faceDetectionConfidence = 0.62f,
            depthGateMinScore = 0.30f,
            depthFlatnessMinScore = 0.50f,
            moireDetectionThreshold = 0.45f,
            screenPatternThreshold = 0.55f,
            photometricMinScore = 0.38f,
            spoofAttemptPenalty = 0.12f,
            staticFrameThreshold = 0.995f,
            minMotionVariance = 0.15f,
            lowLightThreshold = 0.32f,
            lowLightRelaxation = 0.18f,
            screenReflectionMinConfidence = 0.40f,
            screenReflectionMinSignals = 2,
            screenReflectionWeight = 0.12f,
            preprocessingClaheClipLimit = 2.5f
        )
    }

    internal fun toInternal(): InternalPadConfig = OpenPadConfigMapper.toInternal(this)
}
