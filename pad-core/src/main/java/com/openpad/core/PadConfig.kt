package com.openpad.core

/**
 * All configurable thresholds for the Open-PAD pipeline.
 *
 * Classification uses ML models (texture, depth, device) and classical signal
 * processing (frequency, photometric) as sequential decision gates.
 */
data class PadConfig(
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

    // --- ML aggregation weights (sum to ~1.0) ---
    // CDCN is the strongest discriminator (0% EER). MN3 is a fast secondary signal.
    // MiniFASNet texture has 50% EER on print masks — kept lower.
    val textureWeight: Float = 0.15f,
    val mn3Weight: Float = 0.20f,
    val cdcnWeight: Float = 0.55f,
    val deviceWeight: Float = 0.10f,

    // --- Evaluation ---
    val genuineProbabilityThreshold: Float = 0.70f,
    val spoofAttemptPenaltyPerCount: Float = 0.08f,
    val maxGenuineProbabilityThreshold: Float = 0.85f,
    val evaluatingDurationMs: Long = 500L,

    // --- Face consistency (embedding) ---
    /** Minimum cosine similarity between ANALYZING and CHALLENGE checkpoint faces.
     *  Below this = face swap detected -> hard spoof.
     *  MobileFaceNet same-person typically yields 0.85-0.95; different people < 0.75. */
    val faceConsistencyThreshold: Float = 0.70f,

    // --- Timers ---
    val liveSustainMs: Long = 1000L,
    val maxSpoofAttempts: Int = 3,

    // --- Frame rate ---
    val maxFps: Int = 8
) {
    companion object {
        val Default = PadConfig()
    }
}
