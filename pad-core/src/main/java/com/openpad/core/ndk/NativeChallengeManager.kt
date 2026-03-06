package com.openpad.core.ndk

import com.openpad.core.PadResult
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.depth.DepthCharacteristics

/**
 * Challenge manager that delegates phase transitions to the native C layer (NDK).
 * Accumulates ML model scores during the challenge hold phase so the evaluator
 * can compute genuine probability from real model outputs.
 */
internal class NativeChallengeManager : ChallengeManager {

    override var phase: ChallengePhase = ChallengePhase.IDLE
        private set

    private var _evidence = ChallengeEvidence()
    override val evidence: ChallengeEvidence get() = _evidence

    private val accTextureScores = mutableListOf<Float>()
    private val accMn3Scores = mutableListOf<Float>()
    private val accCdcnScores = mutableListOf<Float>()
    private val accDepthCharacteristics = mutableListOf<DepthCharacteristics>()
    private val accReplaySpoofScores = mutableListOf<Float>()
    private val accLuminances = mutableListOf<Float>()
    private var lastHoldFrames = 0

    /**
     * Update state from a PadResult that includes native challenge output.
     * Accumulates ML scores during the CHALLENGE_CLOSER hold phase so that
     * the evaluator can compute genuine probability from real model outputs.
     */
    fun updateFromResult(result: PadResult, nativeOutput: NativeFrameOutput) {
        phase = when (nativeOutput.challengePhase) {
            0 -> ChallengePhase.IDLE
            1 -> ChallengePhase.ANALYZING
            2 -> ChallengePhase.POSITIONING
            3 -> ChallengePhase.CHALLENGE_CLOSER
            4 -> ChallengePhase.EVALUATING
            5 -> ChallengePhase.LIVE
            6 -> ChallengePhase.DONE
            else -> ChallengePhase.IDLE
        }

        if (phase == ChallengePhase.CHALLENGE_CLOSER &&
            nativeOutput.challengeHoldFrames > lastHoldFrames
        ) {
            accTextureScores.add(result.textureResult?.genuineScore ?: 0.5f)
            accMn3Scores.add(result.depthResult?.mn3RealScore ?: 0.5f)
            result.depthResult?.cdcnDepthScore?.let { accCdcnScores.add(it) }
            result.depthResult?.depthCharacteristics?.let { accDepthCharacteristics.add(it) }
            result.replaySpoofResult?.let { accReplaySpoofScores.add(it.spoofScore) }
            accLuminances.add(result.faceLuminance)
        }
        lastHoldFrames = nativeOutput.challengeHoldFrames

        val newCheckpoint1 = if (nativeOutput.captureCheckpoint1) result.faceCropBitmap else _evidence.checkpointBitmapAnalyzing
        val newCheckpoint2 = if (nativeOutput.captureCheckpoint2) result.faceCropBitmap else _evidence.checkpointBitmapChallenge
        val newDisplay1 = if (nativeOutput.captureCheckpoint1) result.faceDisplayBitmap else _evidence.displayBitmapAnalyzing
        val newDisplay2 = if (nativeOutput.captureCheckpoint2) result.faceDisplayBitmap else _evidence.displayBitmapChallenge
        _evidence = ChallengeEvidence(
            totalFrames = nativeOutput.challengeTotalFrames,
            holdFrames = nativeOutput.challengeHoldFrames,
            baselineArea = nativeOutput.challengeBaselineArea,
            maxAreaIncrease = nativeOutput.challengeMaxAreaIncrease,
            holdTextureScores = accTextureScores.toList(),
            holdMn3Scores = accMn3Scores.toList(),
            holdCdcnScores = accCdcnScores.toList(),
            holdDepthCharacteristics = accDepthCharacteristics.toList(),
            holdReplaySpoofScores = accReplaySpoofScores.toList(),
            holdLuminances = accLuminances.toList(),
            checkpointBitmapAnalyzing = newCheckpoint1,
            checkpointBitmapChallenge = newCheckpoint2,
            displayBitmapAnalyzing = newDisplay1,
            displayBitmapChallenge = newDisplay2,
            completed = phase == ChallengePhase.EVALUATING || phase == ChallengePhase.DONE
        )
    }

    override fun onFrame(result: PadResult): ChallengePhase {
        return phase
    }

    override fun advanceToLive() {
        OpenPadNative.nativeChallengeAdvanceToLive()
        phase = ChallengePhase.LIVE
    }

    override fun advanceToDone() {
        OpenPadNative.nativeChallengeAdvanceToDone()
        phase = ChallengePhase.DONE
    }

    override fun handleSpoof(): Boolean {
        val done = OpenPadNative.nativeChallengeHandleSpoof()
        if (done) {
            phase = ChallengePhase.DONE
        } else {
            phase = ChallengePhase.ANALYZING
            _evidence.recycleBitmaps()
            _evidence = ChallengeEvidence()
            clearAccumulatedScores()
        }
        return done
    }

    override fun reset() {
        OpenPadNative.nativeReset()
        phase = ChallengePhase.IDLE
        _evidence.recycleBitmaps()
        _evidence = ChallengeEvidence()
        clearAccumulatedScores()
    }

    private fun clearAccumulatedScores() {
        accTextureScores.clear()
        accMn3Scores.clear()
        accCdcnScores.clear()
        accDepthCharacteristics.clear()
        accReplaySpoofScores.clear()
        accLuminances.clear()
        lastHoldFrames = 0
    }
}
