package com.openpad.core

/**
 * Internal configuration with all thresholds for the Open-PAD pipeline.
 *
 * This is the internal representation -- consumers use [OpenPadConfig] which
 * maps to this via [OpenPadConfigMapper.toInternal].
 *
 * Classification uses ML models (texture, depth, device) and classical signal
 * processing (frequency, photometric) as sequential decision gates.
 */
@ConsistentCopyVisibility
data class InternalPadConfig internal constructor(
    // --- Face detection ---
    val minFaceConfidence: Float = 0.55f,

    // --- Per-frame classifier ---
    /** Minimum texture (MiniFASNet) genuine score for per-frame LIVE. */
    val textureGenuineThreshold: Float = 0.5f,
    /** Frames to collect before first classification decision. */
    val minFramesForDecision: Int = 3,
    /** Sliding window size for temporal feature tracking (movement, blink). */
    val slidingWindowSize: Int = 15,
    /** Consecutive face-detected frames before classification begins. */
    val minConsecutiveFaceFrames: Int = 3,

    // --- Stabilizer ---
    /** Consecutive frames at a non-LIVE status before stabilizer commits (spoof direction). */
    val enterConsecutive: Int = 2,
    /** Consecutive frames at LIVE before stabilizer commits (live direction). */
    val exitConsecutive: Int = 3,

    // --- Positioning ---
    /** Minimum face area (normalized) during ANALYZING — below this show "Move closer". */
    val positioningMinFaceArea: Float = 0.04f,
    /** Maximum face area during ANALYZING — above this show "Move back a little". */
    val positioningMaxFaceArea: Float = 0.45f,
    val positioningCenterTolerance: Float = 0.08f,
    val positioningStableFrames: Int = 5,

    // --- Challenge-response ---
    val challengeCloserMinIncrease: Float = 0.15f,
    /** Frames the user must hold the close+centered pose. */
    val challengeStableFrames: Int = 2,
    /** Minimum total challenge frames before completion. */
    val challengeMinFrames: Int = 3,
    val challengeCenterTolerance: Float = 0.20f,
    /** Face-detected frames in ANALYZING before advancing to challenge. */
    val analyzingStableFrames: Int = 2,

    // --- MN3 cascade gate ---
    /** MN3 real score must be >= this to trigger expensive CDCN inference.
     *  Kept low because MN3 is a fast pre-filter, not a standalone classifier.
     *  MN3 outputs ~0.25-0.75 for real faces depending on image quality. */
    val mn3GateThreshold: Float = 0.20f,

    // --- Depth (CDCN) ---
    /** Hard CDCN depth gate: below this = definitely flat/spoof. */
    val depthFlatnessThreshold: Float = 0.4f,

    // --- Device detection (SSD MobileNet) ---
    /** Minimum confidence for device detection to trigger spoof gate. */
    val deviceConfidenceThreshold: Float = 0.5f,

    // --- Frequency gate (FFT moire + LBP screen) ---
    /** Moire score above this triggers frequency gate. Higher = stricter. */
    val moireThreshold: Float = 0.6f,
    /** LBP screen score above this triggers frequency gate. Higher = stricter. */
    val lbpScreenThreshold: Float = 0.7f,

    // --- Photometric gate ---
    /** Combined photometric score below this triggers spoof gate. Lower = stricter. */
    val photometricMinScore: Float = 0.30f,

    // --- Screen reflection detection (YOLOv5n) ---
    /** Minimum confidence for screen reflection detections to count. */
    val screenReflectionConfidenceThreshold: Float = 0.5f,
    /** Minimum number of overlapping spoof-class detections to trigger the gate. */
    val screenReflectionMinSignals: Int = 2,

    // --- ML aggregation weights (sum to ~1.0) ---
    // CDCN is the strongest discriminator (0% EER). MN3 is a fast secondary signal.
    // MiniFASNet texture has 50% EER on print masks — kept lower.
    val textureWeight: Float = 0.15f,
    val mn3Weight: Float = 0.20f,
    val cdcnWeight: Float = 0.55f,
    val deviceWeight: Float = 0.10f,
    /** Scoring weight for the YOLOv5n screen-reflection detector. */
    val screenReflectionWeight: Float = 0.08f,

    // --- Evaluation ---
    val genuineProbabilityThreshold: Float = 0.70f,
    val spoofAttemptPenaltyPerCount: Float = 0.08f,
    val maxGenuineProbabilityThreshold: Float = 0.90f,
    val evaluatingDurationMs: Long = 500L,

    // --- Face consistency (embedding) ---
    /** Minimum cosine similarity between ANALYZING and CHALLENGE checkpoint faces.
     *  Below this = face swap detected -> hard spoof.
     *  MobileFaceNet same-person typically yields 0.85-0.95; different people < 0.75. */
    val faceConsistencyThreshold: Float = 0.70f,

    // --- Timers ---
    val liveSustainMs: Long = 1000L,
    val maxSpoofAttempts: Int = 2,
    /** Maximum total time (ms) for the entire verification session.
     *  If the session exceeds this, a verdict is forced with accumulated signals. 0 = no timeout. */
    val sessionTimeoutMs: Long = 10_000L,
    /** Maximum time (ms) allowed in the CHALLENGE_CLOSER phase before forcing
     *  an evaluation with whatever signals have been accumulated. 0 = no timeout. */
    val challengeTimeoutMs: Long = 10_000L,

    // --- Frame enhancement ---
    /** Enable ESPCN super-resolution on face regions during CHALLENGE_CLOSER.
     *  The model's quality gate automatically discards enhancements that don't help. */
    val enableFrameEnhancement: Boolean = true,

    // --- Frame preprocessing ---
    /** Enable classical preprocessing (gamma correction + CLAHE) on each frame
     *  before ML inference. Improves accuracy in low-light and uneven lighting. */
    val enablePreprocessing: Boolean = true,
    /** Target luminance for adaptive gamma correction. 0 = disabled.
     *  Values below 0.5 darken bright frames; above 0.5 brighten dark frames. */
    val preprocessingGammaTarget: Float = 0.45f,
    /** CLAHE clip limit controlling contrast amplification. 0 = disabled.
     *  Higher values allow more contrast but may amplify noise. */
    val preprocessingClaheClipLimit: Float = 2.0f,

    // --- Temporal gates ---
    /** Frame similarity above this flags a static image (printed photo).
     *  Real people holding still typically produce 0.985-0.995; actual photos are 0.999+. */
    val staticFrameThreshold: Float = 0.997f,
    /** Head movement variance below this flags a motionless subject (photo/static display). */
    val minMotionVariance: Float = 0.1f,

    // --- Depth temporal variance ---
    /** CDCN score variance below this across hold frames triggers a penalty (flat spoof). */
    val depthScoreVarianceMin: Float = 0.001f,
    /** Quadrant variance consistency below this triggers a penalty (uniform flat surface). */
    val quadrantVarianceMin: Float = 0.0005f,
    /** Multiplicative penalty applied when depth variance is suspiciously low. */
    val depthLowVariancePenalty: Float = 0.85f,

    // --- Ambient light adaptation ---
    /** Face luminance below this is considered low-light.
     *  Dim rooms typically measure 0.25-0.35; raise to catch moderate dimness. */
    val lowLightThreshold: Float = 0.40f,
    /** Face luminance above this is considered harsh-light. */
    val brightLightThreshold: Float = 0.85f,
    /** Threshold relaxation applied in low-light conditions.
     *  Depth models (CDCN, MN3) degrade significantly in low light —
     *  a larger relaxation compensates for lower genuine probability scores. */
    val lowLightRelaxation: Float = 0.30f,
    /** Threshold relaxation applied in harsh-light conditions. */
    val brightLightRelaxation: Float = 0.03f,

    // --- Frame rate ---
    val maxFps: Int = 8
) {
    companion object {
        val Default = InternalPadConfig()
    }
}
