package com.openpad.core.evaluation

import com.openpad.core.InternalPadConfig
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.embedding.FaceEmbeddingAnalyzer

/**
 * Evaluates a challenge attempt after the evaluation delay.
 *
 * Combines face consistency checking and genuine probability scoring
 * into a single decision: live or spoof.
 */
internal object ChallengeEvaluator {

    enum class Verdict { LIVE, SPOOF_FACE_SWAP, SPOOF_LOW_SCORE }

    fun evaluate(
        evidence: ChallengeEvidence,
        config: InternalPadConfig,
        embeddingAnalyzer: FaceEmbeddingAnalyzer?,
        spoofAttemptCount: Int
    ): Verdict {
        val faceConsistent = FaceConsistencyChecker.isConsistent(
            bitmapA = evidence.checkpointBitmapAnalyzing,
            bitmapB = evidence.checkpointBitmapChallenge,
            embeddingAnalyzer = embeddingAnalyzer,
            threshold = config.faceConsistencyThreshold
        )

        if (!faceConsistent) return Verdict.SPOOF_FACE_SWAP

        val genuineProbability = GenuineProbabilityCalculator.compute(evidence, config)
        val avgLuminance = if (evidence.holdLuminances.isNotEmpty()) {
            evidence.holdLuminances.average().toFloat()
        } else 0.5f
        val threshold = GenuineProbabilityCalculator.effectiveThreshold(
            config, spoofAttemptCount, avgLuminance
        )

        return if (genuineProbability >= threshold) Verdict.LIVE else Verdict.SPOOF_LOW_SCORE
    }
}
