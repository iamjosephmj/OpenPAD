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
 * The final liveness score is a weighted sum of four signals. The weights
 * should sum to approximately 1.0:
 * - [textureAnalysisWeight] — surface texture patterns (print/screen artifacts)
 * - [depthAnalysisWeight] — 3D depth map quality (flat = spoof)
 * - [depthGateWeight] — fast depth pre-filter
 * - [screenDetectionWeight] — phone/laptop/tablet screen in frame
 *
 * @property livenessThreshold Minimum overall confidence to accept as live. Range [0.0, 1.0].
 * @property faceMatchThreshold Minimum similarity between checkpoint face captures. Below this = face swap. Range [0.0, 1.0].
 * @property faceDetectionConfidence Minimum confidence for face detection. Range [0.0, 1.0].
 * @property textureAnalysisWeight Scoring weight for texture analysis. Default 0.15.
 * @property depthGateWeight Scoring weight for the fast depth pre-filter. Default 0.20.
 * @property depthAnalysisWeight Scoring weight for full depth map analysis. Default 0.55.
 * @property screenDetectionWeight Scoring weight for screen/device detection. Default 0.10.
 * @property depthGateMinScore Minimum depth gate score to run the full depth analysis. Lower = more permissive.
 * @property depthFlatnessMinScore Hard cutoff: faces flatter than this are rejected as spoof.
 * @property screenDetectionMinConfidence Minimum confidence for screen detection to count as a signal.
 * @property moireDetectionThreshold Moire score above this triggers the frequency gate. Range [0.0, 1.0].
 * @property screenPatternThreshold LBP screen pattern score above this triggers the frequency gate. Range [0.0, 1.0].
 * @property photometricMinScore Combined photometric score below this triggers spoof detection. Range [0.0, 1.0].
 * @property spoofAttemptPenalty Extra threshold added per consecutive failed attempt.
 * @property maxFramesPerSecond Maximum frame processing rate.
 * @property enableDebugOverlay If true, shows real-time debug metrics during the camera phase.
 */
data class OpenPadConfig(
    val livenessThreshold: Float = 0.70f,
    val faceMatchThreshold: Float = 0.70f,
    val faceDetectionConfidence: Float = 0.55f,
    val textureAnalysisWeight: Float = 0.15f,
    val depthGateWeight: Float = 0.20f,
    val depthAnalysisWeight: Float = 0.55f,
    val screenDetectionWeight: Float = 0.10f,
    val depthGateMinScore: Float = 0.20f,
    val depthFlatnessMinScore: Float = 0.40f,
    val screenDetectionMinConfidence: Float = 0.50f,
    val moireDetectionThreshold: Float = 0.60f,
    val screenPatternThreshold: Float = 0.70f,
    val photometricMinScore: Float = 0.30f,
    val spoofAttemptPenalty: Float = 0.08f,
    val maxFramesPerSecond: Int = 8,
    val enableDebugOverlay: Boolean = false,
    /** Enable ESPCN super-resolution on face regions during the closer challenge.
     *  The model's quality gate automatically discards enhancements that don't help. */
    val enableFrameEnhancement: Boolean = true,
) {
    companion object {
        val Default = OpenPadConfig()
    }

    internal fun toPadConfig(): PadConfig = PadConfig(
        maxSpoofAttempts = 0,
        genuineProbabilityThreshold = livenessThreshold,
        faceConsistencyThreshold = faceMatchThreshold,
        minFaceConfidence = faceDetectionConfidence,
        textureWeight = textureAnalysisWeight,
        mn3Weight = depthGateWeight,
        cdcnWeight = depthAnalysisWeight,
        deviceWeight = screenDetectionWeight,
        mn3GateThreshold = depthGateMinScore,
        depthFlatnessThreshold = depthFlatnessMinScore,
        deviceConfidenceThreshold = screenDetectionMinConfidence,
        moireThreshold = moireDetectionThreshold,
        lbpScreenThreshold = screenPatternThreshold,
        photometricMinScore = photometricMinScore,
        spoofAttemptPenaltyPerCount = spoofAttemptPenalty,
        maxFps = maxFramesPerSecond,
        enableFrameEnhancement = enableFrameEnhancement
    )
}
