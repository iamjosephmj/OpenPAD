package com.openpad.core.challenge

import android.graphics.Bitmap
import com.openpad.core.depth.DepthCharacteristics

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
    val holdDepthCharacteristics: List<DepthCharacteristics> = emptyList(),
    /** 112x112 face crop captured at the end of ANALYZING phase (for embedding comparison). */
    val checkpointBitmapAnalyzing: Bitmap? = null,
    /** 112x112 face crop captured at the end of CHALLENGE_CLOSER phase (for embedding comparison). */
    val checkpointBitmapChallenge: Bitmap? = null,
    /** Display-quality face crop at normal distance (preserves natural face size). */
    val displayBitmapAnalyzing: Bitmap? = null,
    /** Display-quality face crop at close distance (preserves natural face size). */
    val displayBitmapChallenge: Bitmap? = null,
    val holdReplaySpoofScores: List<Float> = emptyList(),
    /** Face luminance values collected during hold phase for ambient light adaptation. */
    val holdLuminances: List<Float> = emptyList(),
    val completed: Boolean = false
) {
    /**
     * Recycle all [Bitmap] references held by this evidence and return
     * an empty evidence with no bitmap references. Safe to call multiple
     * times — already-recycled bitmaps are silently skipped.
     */
    fun recycleBitmaps() {
        listOf(
            checkpointBitmapAnalyzing,
            checkpointBitmapChallenge,
            displayBitmapAnalyzing,
            displayBitmapChallenge
        ).forEach { bmp ->
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }
}
