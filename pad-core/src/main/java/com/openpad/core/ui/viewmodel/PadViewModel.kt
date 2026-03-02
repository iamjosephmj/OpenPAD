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
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadResult
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.detection.FaceDetection
import com.openpad.core.embedding.MobileFaceNetAnalyzer
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

internal class PadViewModel : ViewModel() {

    private val pipeline get() = OpenPad.pipeline
    private val config get() = pipeline?.config ?: com.openpad.core.PadConfig.Default

    private val _ui = MutableStateFlow(PadUiState())
    val ui: StateFlow<PadUiState> = _ui.asStateFlow()

    private val _effects = MutableSharedFlow<PadEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<PadEffect> = _effects.asSharedFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var challengeManager: ChallengeManager? = null
    private var liveTimer: Job? = null
    private var evaluateJob: Job? = null
    private var cameraBound = false
    private var spoofAttemptCount = 0
    var outcome: PadOutcome = PadOutcome.Pending
        private set

    fun initFromSdk() {
        val p = pipeline ?: return
        challengeManager = p.createChallengeManager()
        _ui.update { it.copy(isInitialized = true, currentScreen = SdkScreen.INTRO) }
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

            val p = pipeline
            if (p != null) {
                val analyzer = p.createFrameAnalyzer { result ->
                    dispatch(PadIntent.OnAnalyzerResult(result))
                }
                imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
            }

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
            is PadIntent.OnBeginVerification -> {
                _ui.update {
                    it.copy(
                        currentScreen = SdkScreen.CAMERA,
                        phase = ChallengePhase.ANALYZING
                    )
                }
            }

            is PadIntent.OnScreenStarted -> {
                _ui.update { it.copy(phase = ChallengePhase.ANALYZING) }
            }

            is PadIntent.OnPreviewReady -> { }

            is PadIntent.OnAnalyzerResult -> {
                if (_ui.value.verdictState == null && _ui.value.currentScreen == SdkScreen.CAMERA) {
                    handleAnalyzerResult(intent.result)
                }
            }

            is PadIntent.OnCloseClicked -> {
                viewModelScope.launch {
                    _effects.emit(PadEffect.CloseRequested)
                }
            }

            is PadIntent.OnRetryClicked -> handleRetry()

            is PadIntent.OnDone -> {
                viewModelScope.launch {
                    _effects.emit(PadEffect.Done)
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
            ChallengePhase.IDLE -> { }

            ChallengePhase.ANALYZING -> {
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
                if (challenge.phase == ChallengePhase.DONE) {
                    outcome = PadOutcome.SpoofFailed(spoofAttemptCount)
                    _ui.update {
                        it.copy(
                            phase = ChallengePhase.DONE,
                            currentScreen = SdkScreen.VERDICT,
                            challengeProgress = 1f,
                            verdictState = VerdictState.SpoofDetected(
                                attempt = spoofAttemptCount,
                                maxAttempts = config.maxSpoofAttempts,
                                canRetry = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun computePositioningGuidance(result: PadResult): String? {
        val raw = result.rawFaceDetection ?: return null

        val inOval = result.faceDetection != null
        if (!inOval) {
            val cx = raw.centerX
            val cy = raw.centerY
            return when {
                cy < 0.3f -> "Move down a little"
                cy > 0.7f -> "Move up a little"
                cx < 0.3f -> "Move right a little"
                cx > 0.7f -> "Move left a little"
                else -> "Position your face in the frame"
            }
        }

        val area = raw.area
        return when {
            area > config.positioningMaxFaceArea -> "Too close \u2014 move back a little"
            area < config.positioningMinFaceArea -> "Move a bit closer"
            else -> null
        }
    }

    private fun evaluateChallenge(): Job {
        return viewModelScope.launch {
            delay(config.evaluatingDurationMs)

            val challenge = challengeManager ?: return@launch
            val ev = challenge.evidence

            val bitmapA = ev.checkpointBitmapAnalyzing
            val bitmapB = ev.checkpointBitmapChallenge
            val emb = pipeline?.embeddingAnalyzer as? MobileFaceNetAnalyzer

            val faceConsistent = if (bitmapA != null && bitmapB != null && emb != null) {
                val fullBbox = FaceDetection.BBox(0f, 0f, 1f, 1f)
                val pair = emb.analyzePair(bitmapA, fullBbox, bitmapB, fullBbox)
                if (pair != null) {
                    val embA = pair.first.embedding
                    val embB = pair.second.embedding
                    if (embA != null && embB != null) {
                        val similarity = MobileFaceNetAnalyzer.cosineSimilarity(embA, embB)
                        similarity >= config.faceConsistencyThreshold
                    } else true
                } else true
            } else true

            if (!faceConsistent) {
                val terminal = challenge.handleSpoof()
                spoofAttemptCount++
                outcome = PadOutcome.SpoofFailed(spoofAttemptCount)
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.DONE,
                        currentScreen = SdkScreen.VERDICT,
                        challengeProgress = 1f,
                        verdictState = VerdictState.SpoofDetected(
                            attempt = spoofAttemptCount,
                            maxAttempts = config.maxSpoofAttempts,
                            canRetry = !terminal
                        )
                    )
                }
                return@launch
            }

            val genuineProbability = computeGenuineProbability(ev)
            val threshold = (config.genuineProbabilityThreshold +
                spoofAttemptCount * config.spoofAttemptPenaltyPerCount)
                .coerceAtMost(config.maxGenuineProbabilityThreshold)

            if (genuineProbability >= threshold) {
                challenge.advanceToLive()
                startLiveSustainTimer()
            } else {
                val terminal = challenge.handleSpoof()
                spoofAttemptCount++
                outcome = PadOutcome.SpoofFailed(spoofAttemptCount)
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.DONE,
                        currentScreen = SdkScreen.VERDICT,
                        challengeProgress = 1f,
                        verdictState = VerdictState.SpoofDetected(
                            attempt = spoofAttemptCount,
                            maxAttempts = config.maxSpoofAttempts,
                            canRetry = !terminal
                        )
                    )
                }
            }
        }
    }

    private fun handleRetry() {
        val challenge = challengeManager ?: return

        evaluateJob?.cancel()
        evaluateJob = null
        challenge.reset()

        _ui.update {
            PadUiState(
                currentScreen = SdkScreen.CAMERA,
                status = PadStatus.ANALYZING,
                phase = ChallengePhase.ANALYZING,
                isInitialized = true,
                verdictState = null,
                challengeProgress = 0f
            )
        }
        outcome = PadOutcome.Pending
    }

    private fun computeGenuineProbability(
        ev: com.openpad.core.challenge.ChallengeEvidence
    ): Float {
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
                outcome = PadOutcome.LiveConfirmed
                challengeManager?.advanceToDone()
                _ui.update {
                    it.copy(
                        phase = ChallengePhase.DONE,
                        currentScreen = SdkScreen.VERDICT,
                        messageOverride = null,
                        verdictState = VerdictState.LiveConfirmed
                    )
                }
            }
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
        val durationMs = System.currentTimeMillis() - OpenPad.sessionStartMs
        val isLive = outcome is PadOutcome.LiveConfirmed
        val confidence = _ui.value.lastResult?.aggregatedScore ?: 0f
        return OpenPadResult(
            isLive = isLive,
            confidence = confidence,
            durationMs = durationMs,
            spoofAttempts = spoofAttemptCount
        )
    }

    override fun onCleared() {
        super.onCleared()
        liveTimer?.cancel()
        evaluateJob?.cancel()
    }

    companion object
}
