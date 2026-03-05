package com.openpad.core.evaluation

import android.graphics.Bitmap
import com.openpad.core.OpenPadResult
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.depth.DepthCharacteristics

/**
 * Creates [OpenPadResult] instances from challenge evidence.
 *
 * Shared between UI mode and headless mode to ensure consistent
 * result construction.
 *
 * Bitmaps are defensively copied so the consumer receives independent
 * ownership — the SDK can safely recycle its internal evidence bitmaps
 * without invalidating the result returned to the integrator.
 */
internal object OpenPadResultFactory {

    fun create(
        isLive: Boolean,
        confidence: Float,
        sessionStartMs: Long,
        spoofAttemptCount: Int,
        evidence: ChallengeEvidence?
    ): OpenPadResult {
        val normalBmp = evidence?.displayBitmapAnalyzing
            ?: evidence?.checkpointBitmapAnalyzing
        val closeBmp = evidence?.displayBitmapChallenge
            ?: evidence?.checkpointBitmapChallenge

        return OpenPadResult(
            isLive = isLive,
            confidence = confidence,
            durationMs = System.currentTimeMillis() - sessionStartMs,
            spoofAttempts = spoofAttemptCount,
            depthCharacteristics = evidence?.holdDepthCharacteristics?.let {
                DepthCharacteristics.average(it)
            },
            faceAtNormalDistance = normalBmp?.safeCopy(),
            faceAtCloseDistance = closeBmp?.safeCopy()
        )
    }

    private fun Bitmap.safeCopy(): Bitmap? =
        if (!isRecycled) copy(config ?: Bitmap.Config.ARGB_8888, false) else null
}
