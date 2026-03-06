package com.openpad.core.evaluation

import com.openpad.core.InternalPadConfig
import com.openpad.core.challenge.ChallengeEvidence

/**
 * Computes the genuine probability score from challenge evidence.
 *
 * Uses a weighted average of texture, MN3, and CDCN scores, then applies
 * penalties for suspiciously low depth score variance (flat spoofs produce
 * near-identical depth readings across frames).
 *
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

        var score = if (avgCdcn != null) {
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

        score *= depthVariancePenalty(ev, config)

        // Replay spoof penalty disabled: the current model was trained on
        // LFW (studio photos) vs phone-camera replays, causing a severe
        // domain mismatch that produces spoofScore ≈ 1.0 for genuine faces.
        // Re-enable once the model is retrained with real phone-camera data.
        // score *= replaySpoofPenalty(ev)

        return score.coerceIn(0f, 1f)
    }

    /**
     * Multiplicative penalty when CDCN depth scores or quadrant variance
     * show suspiciously low frame-to-frame variation. Real faces produce
     * micro-movement-induced depth variation; flat spoofs do not.
     */
    private fun depthVariancePenalty(ev: ChallengeEvidence, config: InternalPadConfig): Float {
        var penalty = 1.0f

        if (ev.holdCdcnScores.size >= 3) {
            val cdcnVar = variance(ev.holdCdcnScores)
            if (cdcnVar < config.depthScoreVarianceMin) {
                penalty *= config.depthLowVariancePenalty
            }
        }

        if (ev.holdDepthCharacteristics.size >= 3) {
            val qvValues = ev.holdDepthCharacteristics.map { it.quadrantVariance }
            if (variance(qvValues) < config.quadrantVarianceMin) {
                penalty *= config.depthLowVariancePenalty
            }
        }

        return penalty
    }

    /**
     * Multiplicative penalty when the replay spoof detector consistently
     * flags frames as spoofed. The average spoof score across hold frames
     * is inverted to a penalty: avg 0 → no penalty (1.0), avg 1 → full penalty (0.3).
     */
    private fun replaySpoofPenalty(ev: ChallengeEvidence): Float {
        if (ev.holdReplaySpoofScores.size < 3) return 1.0f
        val avgSpoof = ev.holdReplaySpoofScores.average().toFloat()
        if (avgSpoof < REPLAY_SPOOF_THRESHOLD) return 1.0f
        return (1.0f - avgSpoof * REPLAY_SPOOF_MAX_PENALTY).coerceAtLeast(REPLAY_SPOOF_MIN_MULTIPLIER)
    }

    private fun variance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /**
     * Computes the effective threshold after applying per-attempt penalties
     * and ambient light adjustment.
     */
    fun effectiveThreshold(
        config: InternalPadConfig,
        spoofAttemptCount: Int,
        avgLuminance: Float = 0.5f
    ): Float {
        val base = config.genuineProbabilityThreshold +
            spoofAttemptCount * config.spoofAttemptPenaltyPerCount
        val lightAdj = luminanceAdjustment(avgLuminance, config)
        return (base + lightAdj).coerceAtMost(config.maxGenuineProbabilityThreshold)
    }

    /**
     * Adjusts the threshold based on ambient light conditions.
     * Negative return = relax (lower) the threshold.
     */
    internal fun luminanceAdjustment(avgLuminance: Float, config: InternalPadConfig): Float {
        return when {
            avgLuminance < config.lowLightThreshold -> -config.lowLightRelaxation
            avgLuminance > config.brightLightThreshold -> -config.brightLightRelaxation
            else -> 0f
        }
    }

    /** Avg spoof score above this triggers the penalty. */
    private const val REPLAY_SPOOF_THRESHOLD = 0.5f
    /** Max fraction of score removed (0.7 = up to 70% penalty). */
    private const val REPLAY_SPOOF_MAX_PENALTY = 0.7f
    /** Floor: genuine probability is multiplied by at least this. */
    private const val REPLAY_SPOOF_MIN_MULTIPLIER = 0.3f
}
