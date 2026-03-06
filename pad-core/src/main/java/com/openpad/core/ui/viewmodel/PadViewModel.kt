package com.openpad.core.ui.viewmodel

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openpad.core.OpenPadResult
import com.openpad.core.OpenPadThemeConfig
import com.openpad.core.PadPipelineContract
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.evaluation.ChallengeEvaluator
import com.openpad.core.evaluation.OpenPadResultFactory
import com.openpad.core.R
import com.openpad.core.evaluation.PositioningGuidance
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.concurrent.atomic.AtomicBoolean

internal class PadViewModel @AssistedInject constructor(
    private val pipeline: PadPipelineContract,
    @ApplicationContext private val appContext: Context,
    @Assisted private val sessionStartMs: Long,
    @Assisted val callback: PadSessionCallback,
    @Assisted val themeConfig: OpenPadThemeConfig
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            sessionStartMs: Long,
            callback: PadSessionCallback,
            themeConfig: OpenPadThemeConfig,
        ): PadViewModel
    }

    private val config get() = pipeline.config

    private val _ui = MutableStateFlow<PadUiState>(PadUiState.Idle)
    val ui: StateFlow<PadUiState> = _ui.asStateFlow()

    private val _effects = MutableSharedFlow<PadEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<PadEffect> = _effects.asSharedFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var challengeManager: ChallengeManager? = null
    private var liveTimer: Job? = null
    private var evaluateJob: Job? = null
    private var challengeTimeoutJob: Job? = null
    private var sessionTimeoutJob: Job? = null
    private var cameraBound = false
    private var spoofAttemptCount = 0
    private val verdictDelivered = AtomicBoolean(false)
    private var evidenceSnapshot: ChallengeEvidence? = null
    var outcome: PadOutcome = PadOutcome.Pending
        private set

    private val activeState: PadUiState.Active?
        get() = _ui.value as? PadUiState.Active

    private inline fun updateActive(transform: PadUiState.Active.() -> PadUiState.Active) {
        _ui.update { current ->
            when (current) {
                is PadUiState.Active -> current.transform()
                else -> current
            }
        }
    }

    fun initFromSdk() {
        challengeManager = pipeline.createChallengeManager().also { it.reset() }
        _ui.value = PadUiState.Active(phase = ChallengePhase.ANALYZING)
        startSessionTimeoutIfNeeded()
    }

    private fun startSessionTimeoutIfNeeded() {
        if (config.sessionTimeoutMs <= 0L) return
        sessionTimeoutJob = viewModelScope.launch {
            delay(config.sessionTimeoutMs)
            if (!verdictDelivered.get()) onSessionTimeout()
        }
    }

    private fun onSessionTimeout() {
        if (verdictDelivered.get()) return
        val challenge = challengeManager ?: return
        evidenceSnapshot = challenge.evidence
        deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
    }

    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner, analysisExecutor: Executor) {
        if (cameraBound) return
        cameraBound = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val previewResolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(previewResolution)
                .build()
                .apply {
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
                updateActive { copy(phase = ChallengePhase.ANALYZING) }
            }

            is PadIntent.OnPreviewReady -> { }

            is PadIntent.OnAnalyzerResult -> {
                if (!verdictDelivered.get()) {
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

        updateActive { copy(status = status, lastResult = result) }

        val newPhase = challenge.onFrame(result)

        when (newPhase) {
            ChallengePhase.IDLE -> Unit

            ChallengePhase.ANALYZING -> {
                cancelChallengeTimeout()
                updateActive {
                    copy(
                        phase = ChallengePhase.ANALYZING,
                        faceBoxWidthFraction = PadUiState.DEFAULT_FACE_BOX_WIDTH_FRACTION,
                        faceBoxHeightFraction = PadUiState.DEFAULT_FACE_BOX_HEIGHT_FRACTION,
                        challengeProgress = 0f,
                        messageOverride = computePositioningGuidance(result)
                    )
                }
            }

            ChallengePhase.POSITIONING -> {
                updateActive {
                    copy(
                        phase = ChallengePhase.POSITIONING,
                        challengeProgress = 0.1f,
                        messageOverride = computePositioningGuidance(result) ?: appContext.getString(R.string.pad_instruction_center_face)
                    )
                }
            }

            ChallengePhase.CHALLENGE_CLOSER -> {
                startChallengeTimeoutIfNeeded()
                val ev = challenge.evidence
                val msg = if (ev.maxAreaIncrease >= config.challengeCloserMinIncrease) {
                    appContext.getString(R.string.pad_instruction_hold_still)
                } else {
                    appContext.getString(R.string.pad_instruction_move_closer)
                }

                val holdProgress = if (config.challengeStableFrames > 0) {
                    ev.holdFrames.toFloat() / config.challengeStableFrames
                } else 0f

                updateActive {
                    copy(
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
                updateActive {
                    copy(
                        phase = ChallengePhase.EVALUATING,
                        challengeProgress = 0.85f,
                        messageOverride = appContext.getString(R.string.pad_instruction_evaluating)
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
        PositioningGuidance.compute(result, config, appContext)

    private fun evaluateChallenge(): Job {
        return viewModelScope.launch {
            delay(config.evaluatingDurationMs)
            if (verdictDelivered.get()) return@launch

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
                    val terminal = challenge.handleSpoof()
                    spoofAttemptCount++
                    if (terminal) {
                        deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
                    } else {
                        evaluateJob = null
                        updateActive {
                            copy(
                                challengeProgress = 0f,
                                messageOverride = if (verdict == ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP)
                                    appContext.getString(R.string.pad_instruction_try_again) else appContext.getString(R.string.pad_instruction_verification_failed_retry)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startChallengeTimeoutIfNeeded() {
        if (config.challengeTimeoutMs <= 0L) return
        if (challengeTimeoutJob?.isActive == true) return
        challengeTimeoutJob = viewModelScope.launch {
            delay(config.challengeTimeoutMs)
            if (!verdictDelivered.get()) onChallengeTimeout()
        }
    }

    private fun cancelChallengeTimeout() {
        challengeTimeoutJob?.cancel()
        challengeTimeoutJob = null
    }

    private fun onChallengeTimeout() {
        if (verdictDelivered.get()) return
        val challenge = challengeManager ?: return
        val ev = challenge.evidence
        val lastFaceCrop = activeState?.lastResult?.faceCropBitmap
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
                challenge.advanceToDone()
                deliverVerdict(PadOutcome.LiveConfirmed)
            }
            ChallengeEvaluator.Verdict.SPOOF_FACE_SWAP,
            ChallengeEvaluator.Verdict.SPOOF_LOW_SCORE -> {
                deliverVerdict(PadOutcome.SpoofFailed(spoofAttemptCount))
            }
        }
    }

    private fun startLiveSustainTimer() {
        updateActive {
            copy(
                status = PadStatus.LIVE,
                phase = ChallengePhase.LIVE,
                challengeProgress = 1f,
                messageOverride = null
            )
        }

        liveTimer = viewModelScope.launch {
            delay(config.liveSustainMs)

            val current = _ui.value
            if (current is PadUiState.Active &&
                current.status == PadStatus.LIVE &&
                current.phase == ChallengePhase.LIVE
            ) {
                challengeManager?.advanceToDone()
                deliverVerdict(PadOutcome.LiveConfirmed)
            }
        }
    }

    private fun deliverVerdict(result: PadOutcome) {
        if (!verdictDelivered.compareAndSet(false, true)) return
        outcome = result
        sessionTimeoutJob?.cancel()
        challengeTimeoutJob?.cancel()

        val lastResult = activeState?.lastResult
        _ui.value = PadUiState.Done(outcome = result, lastResult = lastResult)

        viewModelScope.launch {
            _effects.emit(PadEffect.Done)
        }
    }

    fun finish() {
        liveTimer?.cancel()
        liveTimer = null
        val current = _ui.value
        if (current is PadUiState.Done) return
        val lastResult = (current as? PadUiState.Active)?.lastResult
        _ui.value = PadUiState.Done(
            outcome = outcome,
            lastResult = lastResult
        )
    }

    fun buildSdkResult(): OpenPadResult {
        val isLive = outcome is PadOutcome.LiveConfirmed
        val lastResult = when (val state = _ui.value) {
            is PadUiState.Active -> state.lastResult
            is PadUiState.Done -> state.lastResult
            is PadUiState.Idle -> null
        }
        val confidence = lastResult?.aggregatedScore ?: 0f
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
        sessionTimeoutJob?.cancel()

        evidenceSnapshot?.recycleBitmaps()
        evidenceSnapshot = null

        challengeManager?.reset()
        challengeManager = null

        val lastResult = when (val state = _ui.value) {
            is PadUiState.Active -> state.lastResult
            is PadUiState.Done -> state.lastResult
            is PadUiState.Idle -> null
        }
        lastResult?.let { last ->
            last.faceCropBitmap?.let { if (!it.isRecycled) it.recycle() }
            last.faceDisplayBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    companion object
}
