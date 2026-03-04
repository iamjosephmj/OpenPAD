package com.openpad.core.evaluation

import com.openpad.core.OpenPadResult
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.depth.DepthCharacteristics

/**
 * Creates [OpenPadResult] instances from challenge evidence.
 *
 * Shared between UI mode and headless mode to ensure consistent
 * result construction.
 */
internal object OpenPadResultFactory {

    fun create(
        isLive: Boolean,
        confidence: Float,
        sessionStartMs: Long,
        spoofAttemptCount: Int,
        evidence: ChallengeEvidence?
    ): OpenPadResult = OpenPadResult(
        isLive = isLive,
        confidence = confidence,
        durationMs = System.currentTimeMillis() - sessionStartMs,
        spoofAttempts = spoofAttemptCount,
        depthCharacteristics = evidence?.holdDepthCharacteristics?.let {
            DepthCharacteristics.average(it)
        },
        faceAtNormalDistance = evidence?.displayBitmapAnalyzing
            ?: evidence?.checkpointBitmapAnalyzing,
        faceAtCloseDistance = evidence?.displayBitmapChallenge
            ?: evidence?.checkpointBitmapChallenge
    )
}
