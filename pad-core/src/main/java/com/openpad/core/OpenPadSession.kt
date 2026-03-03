package com.openpad.core

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.depth.DepthCharacteristics
import com.openpad.core.detection.FaceDetection
import com.openpad.core.embedding.MobileFaceNetAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Headless analysis session for integrators who provide their own camera UI.
 *
 * Runs the full PAD challenge-response pipeline without any SDK-provided screens.
 * The integrator plugs [frameAnalyzer] into their CameraX `ImageAnalysis` and
 * observes the reactive state flows to drive their own UI.
 *
 * Lifecycle: create via [OpenPad.createSession], use, then call [release].
 *
 * ```
 * val session = OpenPad.createSession(listener)
 *
 * // Plug into CameraX
 * imageAnalysis.setAnalyzer(executor, session.frameAnalyzer)
 *
 * // Observe state for your own UI
 * session.status.collect { ... }
 * session.phase.collect { ... }
 * session.instruction.collect { ... }
 * session.challengeProgress.collect { ... }
 *
 * // When done
 * session.release()
 * ```
 */
interface OpenPadSession {

    /** Current PAD classification status (ANALYZING, NO_FACE, LIVE, SPOOF_SUSPECTED, COMPLETED). */
    val status: StateFlow<PadStatus>

    /** Current challenge phase (IDLE → ANALYZING → CHALLENGE_CLOSER → EVALUATING → LIVE → DONE). */
    val phase: StateFlow<ChallengePhase>

    /** Challenge progress in [0.0, 1.0]. Drive a progress bar or arc with this. */
    val challengeProgress: StateFlow<Float>

    /** User-facing instruction text (e.g. "Hold still", "Move closer"). Null when no instruction needed. */
    val instruction: StateFlow<String?>

    /**
     * CameraX-compatible frame analyzer. Plug this into your `ImageAnalysis.setAnalyzer(...)`.
     *
     * Each frame is processed through the full PAD pipeline, and results are
     * automatically fed into the challenge state machine. The reactive state
     * flows update on every frame.
     */
    val frameAnalyzer: ImageAnalysis.Analyzer

    /**
     * Release all resources held by this session. After calling this, the
     * session cannot be reused. Create a new session via [OpenPad.createSession]
     * for another verification attempt.
     */
    fun release()
}

internal class OpenPadSessionImpl(
    private val pipeline: PadPipeline,
    private val config: PadConfig,
    private val listener: OpenPadListener
) : OpenPadSession {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val challengeManager = pipeline.createChallengeManager().also { it.reset() }

    private val _status = MutableStateFlow(PadStatus.ANALYZING)
    override val status: StateFlow<PadStatus> = _status.asStateFlow()

    private val _phase = MutableStateFlow(ChallengePhase.IDLE)
    override val phase: StateFlow<ChallengePhase> = _phase.asStateFlow()

    private val _challengeProgress = MutableStateFlow(0f)
    override val challengeProgress: StateFlow<Float> = _challengeProgress.asStateFlow()

    private val _instruction = MutableStateFlow<String?>("Hold still and look at the camera")
    override val instruction: StateFlow<String?> = _instruction.asStateFlow()

    private var evaluateJob: Job? = null
    private var liveTimer: Job? = null
    private var spoofAttemptCount = 0
    private var sessionStartMs = System.currentTimeMillis()
    private var delivered = false
    /** Snapshot of evidence taken at evaluation time, before handleSpoof can clear it. */
    private var evidenceSnapshot: ChallengeEvidence? = null

    override val frameAnalyzer: ImageAnalysis.Analyzer =
        pipeline.createFrameAnalyzer { result -> handleResult(result) }

    private fun handleResult(result: PadResult) {
        if (delivered) return

        val newPhase = challengeManager.onFrame(result)
        _status.value = result.status
        _phase.value = newPhase

        when (newPhase) {
            ChallengePhase.IDLE -> {}

            ChallengePhase.ANALYZING -> {
                _challengeProgress.value = 0f
                _instruction.value = computePositioningGuidance(result)
                    ?: "Hold still and look at the camera"
            }

            ChallengePhase.POSITIONING -> {
                _challengeProgress.value = 0.1f
                _instruction.value = computePositioningGuidance(result) ?: "Center your face"
            }

            ChallengePhase.CHALLENGE_CLOSER -> {
                val ev = challengeManager.evidence
                val msg = if (ev.maxAreaIncrease >= config.challengeCloserMinIncrease) {
                    "Hold still\u2026"
                } else {
                    "Move closer"
                }
                val holdProgress = if (config.challengeStableFrames > 0) {
                    ev.holdFrames.toFloat() / config.challengeStableFrames
                } else 0f
                _challengeProgress.value = 0.2f + holdProgress * 0.6f
                _instruction.value = msg
            }

            ChallengePhase.EVALUATING -> {
                _challengeProgress.value = 0.85f
                _instruction.value = "Evaluating\u2026"
                if (evaluateJob == null || evaluateJob?.isActive != true) {
                    evaluateJob = evaluateChallenge()
                }
            }

            ChallengePhase.LIVE -> {}

            ChallengePhase.DONE -> {
                if (!delivered && challengeManager.phase == ChallengePhase.DONE) {
                    _challengeProgress.value = 1f
                    _instruction.value = null
                    deliverSpoofResult()
                }
            }
        }
    }

    private fun computePositioningGuidance(result: PadResult): String? {
        val raw = result.rawFaceDetection ?: return null
        val inOval = result.faceDetection != null
        if (!inOval) {
            return when {
                raw.centerY < 0.3f -> "Move down a little"
                raw.centerY > 0.7f -> "Move up a little"
                raw.centerX < 0.3f -> "Move right a little"
                raw.centerX > 0.7f -> "Move left a little"
                else -> "Position your face in the frame"
            }
        }
        return when {
            raw.area > config.positioningMaxFaceArea -> "Too close \u2014 move back a little"
            raw.area < config.positioningMinFaceArea -> "Move a bit closer"
            else -> null
        }
    }

    private fun evaluateChallenge(): Job = scope.launch {
        delay(config.evaluatingDurationMs)
        if (delivered) return@launch

        val ev = challengeManager.evidence
        evidenceSnapshot = ev
        val emb = pipeline.embeddingAnalyzer as? MobileFaceNetAnalyzer
        val bitmapA = ev.checkpointBitmapAnalyzing
        val bitmapB = ev.checkpointBitmapChallenge

        val faceConsistent = checkFaceConsistency(bitmapA, bitmapB, emb)

        if (!faceConsistent) {
            val terminal = challengeManager.handleSpoof()
            spoofAttemptCount++
            if (terminal) {
                deliverSpoofResult()
            } else {
                _challengeProgress.value = 0f
                _instruction.value = "Try again"
            }
            return@launch
        }

        val genuineProbability = computeGenuineProbability(ev)
        val threshold = (config.genuineProbabilityThreshold +
            spoofAttemptCount * config.spoofAttemptPenaltyPerCount)
            .coerceAtMost(config.maxGenuineProbabilityThreshold)

        if (genuineProbability >= threshold) {
            challengeManager.advanceToLive()
            startLiveSustainTimer()
        } else {
            val terminal = challengeManager.handleSpoof()
            spoofAttemptCount++
            if (terminal) {
                deliverSpoofResult()
            } else {
                _challengeProgress.value = 0f
                _instruction.value = "Verification failed \u2014 trying again"
            }
        }
    }

    private fun checkFaceConsistency(
        bitmapA: android.graphics.Bitmap?,
        bitmapB: android.graphics.Bitmap?,
        emb: MobileFaceNetAnalyzer?
    ): Boolean {
        if (bitmapA == null || bitmapB == null || emb == null) return true
        val fullBbox = FaceDetection.BBox(0f, 0f, 1f, 1f)
        val pair = emb.analyzePair(bitmapA, fullBbox, bitmapB, fullBbox) ?: return true
        val embA = pair.first.embedding ?: return true
        val embB = pair.second.embedding ?: return true
        val similarity = MobileFaceNetAnalyzer.cosineSimilarity(embA, embB)
        return similarity >= config.faceConsistencyThreshold
    }

    private fun computeGenuineProbability(ev: ChallengeEvidence): Float {
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

    private fun startLiveSustainTimer() {
        _status.value = PadStatus.LIVE
        _phase.value = ChallengePhase.LIVE
        _challengeProgress.value = 1f
        _instruction.value = null

        liveTimer = scope.launch {
            delay(config.liveSustainMs)
            if (!delivered) {
                challengeManager.advanceToDone()
                deliverLiveResult()
            }
        }
    }

    private fun deliverLiveResult() {
        if (delivered) return
        delivered = true
        val ev = evidenceSnapshot ?: challengeManager.evidence
        val result = OpenPadResult(
            isLive = true,
            confidence = _challengeProgress.value,
            durationMs = System.currentTimeMillis() - sessionStartMs,
            spoofAttempts = spoofAttemptCount,
            depthCharacteristics = DepthCharacteristics.average(ev.holdDepthCharacteristics),
            faceAtNormalDistance = ev.displayBitmapAnalyzing ?: ev.checkpointBitmapAnalyzing,
            faceAtCloseDistance = ev.displayBitmapChallenge ?: ev.checkpointBitmapChallenge
        )
        _status.value = PadStatus.COMPLETED
        _phase.value = ChallengePhase.DONE
        mainHandler.post { listener.onLiveConfirmed(result) }
    }

    private fun deliverSpoofResult() {
        if (delivered) return
        delivered = true
        val ev = evidenceSnapshot ?: challengeManager.evidence
        val result = OpenPadResult(
            isLive = false,
            confidence = _challengeProgress.value,
            durationMs = System.currentTimeMillis() - sessionStartMs,
            spoofAttempts = spoofAttemptCount,
            depthCharacteristics = DepthCharacteristics.average(ev.holdDepthCharacteristics),
            faceAtNormalDistance = ev.displayBitmapAnalyzing ?: ev.checkpointBitmapAnalyzing,
            faceAtCloseDistance = ev.displayBitmapChallenge ?: ev.checkpointBitmapChallenge
        )
        _status.value = PadStatus.COMPLETED
        _phase.value = ChallengePhase.DONE
        _instruction.value = null
        mainHandler.post { listener.onSpoofDetected(result) }
    }

    override fun release() {
        if (!delivered) {
            delivered = true
            mainHandler.post { listener.onCancelled() }
        }
        evaluateJob?.cancel()
        liveTimer?.cancel()
        scope.cancel()
    }

    companion object
}
