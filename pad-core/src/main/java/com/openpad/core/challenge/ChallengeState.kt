package com.openpad.core.challenge

import android.graphics.Bitmap

/** Phases of the challenge-response state machine. */
enum class ChallengePhase {
    IDLE,
    ANALYZING,
    POSITIONING,
    CHALLENGE_CLOSER,
    EVALUATING,
    LIVE,
    DONE
}

/**
 * Accumulated evidence during the challenge phase.
 * ML model scores collected: texture (MiniFASNet) + MN3 + CDCN (when available).
 * Face consistency: two checkpoint bitmaps captured during ANALYZING and CHALLENGE_CLOSER,
 * compared via face embedding model at verdict time.
 */
data class ChallengeEvidence(
    val totalFrames: Int = 0,
    val holdFrames: Int = 0,
    val baselineArea: Float = 0f,
    val maxAreaIncrease: Float = 0f,
    val holdTextureScores: List<Float> = emptyList(),
    val holdMn3Scores: List<Float> = emptyList(),
    val holdCdcnScores: List<Float> = emptyList(),
    /** Face crop captured at the end of ANALYZING phase (before challenge starts). */
    val checkpointBitmapAnalyzing: Bitmap? = null,
    /** Face crop captured at the end of CHALLENGE_CLOSER phase (hold complete). */
    val checkpointBitmapChallenge: Bitmap? = null,
    val completed: Boolean = false
)
