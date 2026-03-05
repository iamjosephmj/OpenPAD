package com.openpad.core.ui.viewmodel

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openpad.core.OpenPadResult
import com.openpad.core.OpenPadThemeConfig
import com.openpad.core.PadPipeline
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.evaluation.ChallengeEvaluator
import com.openpad.core.evaluation.OpenPadResultFactory
import com.openpad.core.evaluation.PositioningGuidance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

internal class PadViewModel(
    private val pipeline: PadPipeline,
    private val sessionStartMs: Long,
    val callback: PadSessionCallback,
    val themeConfig: OpenPadThemeConfig
) : ViewModel() {

    private val config get() = pipeline.config

    private val _ui = MutableStateFlow(PadUiState())
    val ui: StateFlow<PadUiState> = _ui.asStateFlow()

    private val _effects = MutableSharedFlow<PadEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<PadEffect> = _effects.asSharedFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var challengeManager: ChallengeManager? = null
    private var liveTimer: Job? = null
    private var evaluateJob: Job? = null
    private var challengeTimeoutJob: Job? = null
    private var cameraBound = false
    private var spoofAttemptCount = 0
    private var verdictDelivered = false
    /** Snapshot of evidence taken at evaluation time, before handleSpoof can clear it. */
    private var evidenceSnapshot: ChallengeEvidence? = null
    var outcome: PadOutcome = PadOutcome.Pending
        private set

    fun initFromSdk() {
        challengeManager = pipeline.createChallengeManager().also { it.reset() }
        _ui.update { it.copy(isInitialized = true, phase = ChallengePhase.ANALYZING) }
    }

    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner, analysisExecutor: Executor) {
        if (cameraBound) return
        cameraBound = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request ->
                    _surfaceRequest.value = request
                }
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer = pipeline.createFrameAnalyzer { result ->
                dispatch(PadIntent.OnAnalyzerResult(result))
            }
            imageAnalysis.setAnalyzer(analysisExecutor, analyzer)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalysis
            )

            dispatch(PadIntent.OnPreviewReady)
        }, ContextCompat.getMainExecutor(context))
    }

    fun dispatch(intent: PadIntent) {
        when (intent) {
            is PadIntent.OnScreenStarted -> {
                _ui.update { it.copy(phase = ChallengePhase.ANALYZING) }
            }

            is PadIntent.OnPreviewReady -> { }

            is PadIntent.OnAnalyzerResult -> {
                if (!verdictDelivered) {
                    handleAnalyzerResult(intent.result)
                }
            }

            is PadIntent.OnCloseClicked -> {
                viewModelScope.launch {
                    _effects.emit(PadEffect.CloseRequested)
                }
            }

            is PadIntent.OnScreenDisposed -> {
                finish()
            }
        }
    }

    private fun handleAnalyzerResult(result: PadResult) {
        val challenge = challengeManager ?: return
        val status = result.status

        _ui.update { it.copy(status = status, lastResult = result) }

        val newPhase = challenge.onFrame(result)

        when (newPhase) {
            ChallengePhase.IDLE -> Unit

            ChallengePhase.ANALYZING -> {
                cancelChallengeTimeout()
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.ANALYZING,
                        faceBoxWidthFraction = PadUiState.DEFAULT_FACE_BOX_WIDTH_FRACTION,
                        faceBoxHeightFraction = PadUiState.DEFAULT_FACE_BOX_HEIGHT_FRACTION,
                        challengeProgress = 0f,
                        messageOverride = computePositioningGuidance(result)
                    )
                }
            }

            ChallengePhase.POSITIONING -> {
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.POSITIONING,
                        challengeProgress = 0.1f,
                        messageOverride = computePositioningGuidance(result) ?: "Center your face"
                    )
                }
            }

            ChallengePhase.CHALLENGE_CLOSER -> {
                startChallengeTimeoutIfNeeded()
                val ev = challenge.evidence
                val msg = if (ev.maxAreaIncrease >= config.challengeCloserMinIncrease) {
                    "Hold still\u2026"
                } else {
                    "Move closer"
                }

                val holdProgress = if (config.challengeStableFrames > 0) {
                    ev.holdFrames.toFloat() / config.challengeStableFrames
                } else 0f

                _ui.update {
                    it.copy(
                        phase = ChallengePhase.CHALLENGE_CLOSER,
                        faceBoxWidthFraction = PadUiState.CHALLENGE_FACE_BOX_WIDTH_FRACTION,
                        faceBoxHeightFraction = PadUiState.CHALLENGE_FACE_BOX_HEIGHT_FRACTION,
                        challengeProgress = 0.2f + holdProgress * 0.6f,
                        messageOverride = msg
                    )
                }
            }

            ChallengePhase.EVALUATING -> {
                cancelChallengeTimeout()
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.EVALUATING,
                        challengeProgress = 0.85f,
                        messageOverride = "Evaluating\u2026"
                    )
                }
                if (evaluateJob == null || evaluateJob?.isActive != true) {
                    evaluateJob = evaluateChallenge()
                }
            }

            ChallengePhase.LIVE -> { }

            ChallengePhase.DONE -> {
                cancelChallengeTimeout()
                if (challenge.phase == ChallengePhase.DONE) {
                    deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
                }
            }
        }
    }

    private fun computePositioningGuidance(result: PadResult): String? =
        PositioningGuidance.compute(result, config)

    private fun evaluateChallenge(): Job {
        return viewModelScope.launch {
            delay(config.evaluatingDurationMs)

            val challenge = challengeManager ?: return@launch
            val ev = challenge.evidence
            evidenceSnapshot = ev

            val verdict = ChallengeEvaluator.evaluate(
                evidence = ev,
                config = config,
                embeddingAnalyzer = pipeline.embeddingAnalyzer,
                spoofAttemptCount = spoofAttemptCount
            )

            when (verdict) {
                ChallengeEvaluator.Verdict.LIVE -> {
                    challenge.advanceToLive()
                    startLiveSustainTimer()
                }
                ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP,
                ChallengeEvaluator.Verdict.SPOOF_LOW_SCORE -> {
                    challenge.handleSpoof()
                    spoofAttemptCount++
                    deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
                }
            }
        }
    }

    private fun startChallengeTimeoutIfNeeded() {
        if (config.challengeTimeoutMs <= 0L) return
        if (challengeTimeoutJob?.isActive == true) return
        challengeTimeoutJob = viewModelScope.launch {
            delay(config.challengeTimeoutMs)
            if (!verdictDelivered) onChallengeTimeout()
        }
    }

    private fun cancelChallengeTimeout() {
        challengeTimeoutJob?.cancel()
        challengeTimeoutJob = null
    }

    private fun onChallengeTimeout() {
        if (verdictDelivered) return
        val challenge = challengeManager ?: return
        val ev = challenge.evidence
        val lastFaceCrop = _ui.value.lastResult?.faceCropBitmap
        val timeoutEvidence = if (ev.checkpointBitmapChallenge == null && lastFaceCrop != null) {
            ev.copy(checkpointBitmapChallenge = lastFaceCrop)
        } else ev
        evidenceSnapshot = timeoutEvidence

        val verdict = ChallengeEvaluator.evaluate(
            evidence = timeoutEvidence,
            config = config,
            embeddingAnalyzer = pipeline.embeddingAnalyzer,
            spoofAttemptCount = spoofAttemptCount
        )

        when (verdict) {
            ChallengeEvaluator.Verdict.LIVE -> {
                challenge.advanceToLive()
                startLiveSustainTimer()
            }
            ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP,
            ChallengeEvaluator.Verdict.SPOOF_LOW_SCORE -> {
                deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
            }
        }
    }

    private fun startLiveSustainTimer() {
        _ui.update {
            it.copy(
                status = PadStatus.LIVE,
                phase = ChallengePhase.LIVE,
                challengeProgress = 1f,
                messageOverride = null
            )
        }

        liveTimer = viewModelScope.launch {
            delay(config.liveSustainMs)

            val current = _ui.value
            if (current.status == PadStatus.LIVE && current.phase == ChallengePhase.LIVE) {
                challengeManager?.advanceToDone()
                deliverVerdict(PadOutcome.LiveConfirmed)
            }
        }
    }

    /** Deliver the verdict result and signal the activity to finish. */
    private fun deliverVerdict(result: PadOutcome) {
        if (verdictDelivered) return
        verdictDelivered = true
        outcome = result

        _ui.update {
            it.copy(
                phase = ChallengePhase.DONE,
                challengeProgress = 1f,
                messageOverride = null
            )
        }

        viewModelScope.launch {
            _effects.emit(PadEffect.Done)
        }
    }

    fun finish() {
        liveTimer?.cancel()
        liveTimer = null
        _ui.update {
            it.copy(
                status = PadStatus.COMPLETED,
                phase = ChallengePhase.DONE,
                messageOverride = null
            )
        }
    }

    fun buildSdkResult(): OpenPadResult {
        val isLive = outcome is PadOutcome.LiveConfirmed
        val confidence = _ui.value.lastResult?.aggregatedScore ?: 0f
        val ev = evidenceSnapshot ?: challengeManager?.evidence
        return OpenPadResultFactory.create(
            isLive = isLive,
            confidence = confidence,
            sessionStartMs = sessionStartMs,
            spoofAttemptCount = spoofAttemptCount,
            evidence = ev
        )
    }

    override fun onCleared() {
        super.onCleared()
        liveTimer?.cancel()
        evaluateJob?.cancel()
        challengeTimeoutJob?.cancel()

        evidenceSnapshot?.recycleBitmaps()
        evidenceSnapshot = null

        // reset() both recycles evidence bitmaps AND replaces _evidence
        // with a fresh empty instance, preventing the next session from
        // inheriting recycled bitmap references via the shared manager.
        challengeManager?.reset()
        challengeManager = null

        _ui.value.lastResult?.let { last ->
            last.faceCropBitmap?.let { if (!it.isRecycled) it.recycle() }
            last.faceDisplayBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    companion object
}
