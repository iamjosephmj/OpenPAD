package com.openpad.core

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.evaluation.ChallengeEvaluator
import com.openpad.core.evaluation.OpenPadResultFactory
import com.openpad.core.evaluation.PositioningGuidance
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
    private val config: InternalPadConfig,
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

    private fun computePositioningGuidance(result: PadResult): String? =
        PositioningGuidance.compute(result, config)

    private fun evaluateChallenge(): Job = scope.launch {
        delay(config.evaluatingDurationMs)
        if (delivered) return@launch

        val ev = challengeManager.evidence
        evidenceSnapshot = ev

        val verdict = ChallengeEvaluator.evaluate(
            evidence = ev,
            config = config,
            embeddingAnalyzer = pipeline.embeddingAnalyzer,
            spoofAttemptCount = spoofAttemptCount
        )

        when (verdict) {
            ChallengeEvaluator.Verdict.LIVE -> {
                challengeManager.advanceToLive()
                startLiveSustainTimer()
            }
            ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP,
            ChallengeEvaluator.Verdict.SPOOF_LOW_SCORE -> {
                val terminal = challengeManager.handleSpoof()
                spoofAttemptCount++
                if (terminal) {
                    deliverSpoofResult()
                } else {
                    _challengeProgress.value = 0f
                    _instruction.value = if (verdict == ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP)
                        "Try again" else "Verification failed \u2014 trying again"
                }
            }
        }
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
        val result = OpenPadResultFactory.create(
            isLive = true,
            confidence = _challengeProgress.value,
            sessionStartMs = sessionStartMs,
            spoofAttemptCount = spoofAttemptCount,
            evidence = ev
        )
        _status.value = PadStatus.COMPLETED
        _phase.value = ChallengePhase.DONE
        mainHandler.post { listener.onLiveConfirmed(result) }
    }

    private fun deliverSpoofResult() {
        if (delivered) return
        delivered = true
        val ev = evidenceSnapshot ?: challengeManager.evidence
        val result = OpenPadResultFactory.create(
            isLive = false,
            confidence = _challengeProgress.value,
            sessionStartMs = sessionStartMs,
            spoofAttemptCount = spoofAttemptCount,
            evidence = ev
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
