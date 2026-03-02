package com.openpad.core.challenge

import com.openpad.core.PadConfig
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import timber.log.Timber
import kotlin.math.abs

/**
 * Layer 5: Challenge-response state machine.
 *
 * Phases: IDLE → ANALYZING → CHALLENGE_CLOSER → EVALUATING → LIVE → DONE
 *
 * ANALYZING detects a face and establishes an area baseline.
 * CHALLENGE_CLOSER requires the face area to increase from baseline.
 * Hold-phase gates: sharpness, frame similarity.
 * Probabilistic evaluation with spoof attempt tracking.
 */
class MovementChallenge(
    private val config: PadConfig = PadConfig.Default
) : ChallengeManager {

    override var phase: ChallengePhase = ChallengePhase.IDLE
        private set

    private var _evidence = ChallengeEvidence()
    override val evidence: ChallengeEvidence get() = _evidence

    private var baselineArea: Float = 0f
    private var analyzingLiveFrames: Int = 0
    private var challengeHoldFrames: Int = 0
    private var spoofAttemptCount: Int = 0
    private var baselineCalibrated: Boolean = false

    private val baselineAreas = mutableListOf<Float>()

    override fun onFrame(result: PadResult): ChallengePhase {
        val face = result.faceDetection

        when (phase) {
            ChallengePhase.IDLE -> {
                phase = ChallengePhase.ANALYZING
                Timber.tag(TAG).d("Challenge: IDLE → ANALYZING")
            }

            ChallengePhase.ANALYZING -> {
                if (face == null) {
                    analyzingLiveFrames = 0
                    return phase
                }

                analyzingLiveFrames++

                if (analyzingLiveFrames >= config.analyzingStableFrames) {
                    baselineCalibrated = false
                    baselineAreas.clear()
                    phase = ChallengePhase.CHALLENGE_CLOSER
                    Timber.tag(TAG).d("Challenge: ANALYZING → CHALLENGE_CLOSER")
                }
            }

            ChallengePhase.POSITIONING -> {
                phase = ChallengePhase.CHALLENGE_CLOSER
            }

            ChallengePhase.CHALLENGE_CLOSER -> {
                if (face == null || result.status == PadStatus.NO_FACE) {
                    challengeHoldFrames = 0
                    return phase
                }

                val area = face.area

                // Calibrate baseline from the first few frames of this phase
                if (!baselineCalibrated) {
                    baselineAreas.add(area)
                    if (baselineAreas.size >= BASELINE_CALIBRATION_FRAMES) {
                        baselineArea = baselineAreas.average().toFloat()
                        baselineCalibrated = true
                        // Capture checkpoint 1: face at normal distance, right before zoom
                        _evidence = ChallengeEvidence(
                            baselineArea = baselineArea,
                            checkpointBitmapAnalyzing = result.faceCropBitmap
                        )
                        Timber.tag(TAG).d(
                            "Challenge: baseline calibrated=%.4f, captured pre-zoom checkpoint",
                            baselineArea
                        )
                    }
                    return phase
                }

                val areaIncrease = if (baselineArea > 0) (area - baselineArea) / baselineArea else 0f
                val maxIncrease = maxOf(_evidence.maxAreaIncrease, areaIncrease)

                val centered = abs(face.centerX - 0.5f) <= config.challengeCenterTolerance &&
                    abs(face.centerY - 0.5f) <= config.challengeCenterTolerance

                val closerEnough = areaIncrease >= config.challengeCloserMinIncrease
                val totalFrames = _evidence.totalFrames + 1

                Timber.tag(TAG).v(
                    "Challenge: area=%.4f base=%.4f inc=%.3f centered=%s closer=%s hold=%d/%d total=%d/%d",
                    area, baselineArea, areaIncrease, centered, closerEnough,
                    challengeHoldFrames, config.challengeStableFrames,
                    totalFrames, config.challengeMinFrames
                )

                if (closerEnough && centered) {
                    challengeHoldFrames++

                    val depthResult = result.depthResult

                    _evidence = _evidence.copy(
                        totalFrames = totalFrames,
                        holdFrames = challengeHoldFrames,
                        maxAreaIncrease = maxIncrease,
                        holdTextureScores = _evidence.holdTextureScores +
                            (result.textureResult?.genuineScore ?: 0.5f),
                        holdMn3Scores = _evidence.holdMn3Scores +
                            (depthResult?.mn3RealScore ?: 0.5f),
                        holdCdcnScores = if (depthResult?.cdcnDepthScore != null) {
                            _evidence.holdCdcnScores + depthResult.cdcnDepthScore
                        } else {
                            _evidence.holdCdcnScores
                        }
                    )

                    if (challengeHoldFrames >= config.challengeStableFrames &&
                        totalFrames >= config.challengeMinFrames
                    ) {
                        // Capture face crop at checkpoint 2 (hold complete)
                        val crop = result.faceCropBitmap
                        if (crop != null) {
                            _evidence = _evidence.copy(checkpointBitmapChallenge = crop)
                            Timber.tag(TAG).d("Challenge: captured CHALLENGE checkpoint bitmap")
                        }

                        _evidence = _evidence.copy(completed = true)
                        phase = ChallengePhase.EVALUATING
                        Timber.tag(TAG).d("Challenge: CHALLENGE_CLOSER → EVALUATING")
                    }
                } else {
                    // Decrement instead of hard reset — tolerate occasional jitter
                    challengeHoldFrames = (challengeHoldFrames - 2).coerceAtLeast(0)
                    _evidence = _evidence.copy(
                        totalFrames = totalFrames,
                        maxAreaIncrease = maxIncrease
                    )
                }
            }

            ChallengePhase.EVALUATING -> {
                // Evaluation happens externally (ViewModel decides LIVE vs SPOOF)
            }

            ChallengePhase.LIVE -> {
                // Sustain period — monitored by ViewModel timer
            }

            ChallengePhase.DONE -> {
                // Terminal state
            }
        }

        return phase
    }

    override fun advanceToLive() {
        phase = ChallengePhase.LIVE
        Timber.tag(TAG).d("Challenge: → LIVE")
    }

    override fun advanceToDone() {
        phase = ChallengePhase.DONE
        Timber.tag(TAG).d("Challenge: → DONE")
    }

    override fun handleSpoof(): Boolean {
        spoofAttemptCount++
        Timber.tag(TAG).d("Challenge: SPOOF (attempt #%d/%d)", spoofAttemptCount, config.maxSpoofAttempts)

        if (config.maxSpoofAttempts > 0 && spoofAttemptCount >= config.maxSpoofAttempts) {
            phase = ChallengePhase.DONE
            return true
        }

        phase = ChallengePhase.ANALYZING
        analyzingLiveFrames = 0
        challengeHoldFrames = 0
        baselineCalibrated = false
        baselineAreas.clear()
        _evidence = ChallengeEvidence()
        return false
    }

    override fun reset() {
        phase = ChallengePhase.IDLE
        _evidence = ChallengeEvidence()
        baselineArea = 0f
        baselineCalibrated = false
        analyzingLiveFrames = 0
        challengeHoldFrames = 0
        spoofAttemptCount = 0
        baselineAreas.clear()
    }

    companion object {
        private const val TAG = "PAD"
        private const val BASELINE_CALIBRATION_FRAMES = 2
    }
}
