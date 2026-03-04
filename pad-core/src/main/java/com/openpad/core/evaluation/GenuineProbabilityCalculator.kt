package com.openpad.core.evaluation

import com.openpad.core.InternalPadConfig
import com.openpad.core.challenge.ChallengeEvidence

/**
 * Computes the genuine probability score from challenge evidence.
 *
 * Uses a weighted average of texture, MN3, and CDCN scores.
 * When CDCN is unavailable, its weight is redistributed to texture and MN3.
 */
internal object GenuineProbabilityCalculator {

    fun compute(ev: ChallengeEvidence, config: InternalPadConfig): Float {
        val avgTexture = if (ev.holdTextureScores.isNotEmpty()) {
            ev.holdTextureScores.average().toFloat()
        } else 0.5f

        val avgMn3 = if (ev.holdMn3Scores.isNotEmpty()) {
            ev.holdMn3Scores.average().toFloat()
        } else 0.5f

        val avgCdcn = if (ev.holdCdcnScores.isNotEmpty()) {
            ev.holdCdcnScores.average().toFloat()
        } else null

        val score = if (avgCdcn != null) {
            val totalW = config.textureWeight + config.mn3Weight + config.cdcnWeight
            (avgTexture * config.textureWeight +
                avgMn3 * config.mn3Weight +
                avgCdcn * config.cdcnWeight) / totalW
        } else {
            val cdcnRedist = config.cdcnWeight
            val effectiveTextureW = config.textureWeight + cdcnRedist * 2f / 3f
            val effectiveMn3W = config.mn3Weight + cdcnRedist * 1f / 3f
            val totalW = effectiveTextureW + effectiveMn3W
            (avgTexture * effectiveTextureW + avgMn3 * effectiveMn3W) / totalW
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Computes the effective threshold after applying per-attempt penalties.
     */
    fun effectiveThreshold(config: InternalPadConfig, spoofAttemptCount: Int): Float =
        (config.genuineProbabilityThreshold +
            spoofAttemptCount * config.spoofAttemptPenaltyPerCount)
            .coerceAtMost(config.maxGenuineProbabilityThreshold)
}
