package com.openpad.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadConfig
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadListener
import com.openpad.core.OpenPadResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sdkState: SdkState = SdkState.Initializing,
        val config: OpenPadConfig = OpenPadConfig(),
        val showConfig: Boolean = false,
        val showResult: Boolean = false,
        val lastVerification: VerificationResult? = null
    )

    enum class SdkState { Initializing, Ready, Error }

    data class VerificationResult(
        val isLive: Boolean,
        val confidence: Float,
        val durationMs: Long,
        val spoofAttempts: Int,
        val result: OpenPadResult
    )

    sealed interface SideEffect {
        data object LaunchVerification : SideEffect
        data class LaunchHeadless(val listener: OpenPadListener) : SideEffect
        data class Toast(val message: String) : SideEffect
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _sideEffect = Channel<SideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private var appliedConfig: OpenPadConfig? = null

    val listener = object : OpenPadListener {
        override fun onLiveConfirmed(result: OpenPadResult) {
            Timber.d("LIVE  confidence=%.2f  duration=%dms", result.confidence, result.durationMs)
            _state.update {
                it.copy(
                    showResult = true,
                    lastVerification = VerificationResult(
                        isLive = true,
                        confidence = result.confidence,
                        durationMs = result.durationMs,
                        spoofAttempts = result.spoofAttempts,
                        result = result
                    )
                )
            }
        }

        override fun onSpoofDetected(result: OpenPadResult) {
            Timber.d("SPOOF  confidence=%.2f  duration=%dms", result.confidence, result.durationMs)
            _state.update {
                it.copy(
                    showResult = true,
                    lastVerification = VerificationResult(
                        isLive = false,
                        confidence = result.confidence,
                        durationMs = result.durationMs,
                        spoofAttempts = result.spoofAttempts,
                        result = result
                    )
                )
            }
        }

        override fun onError(error: OpenPadError) {
            Timber.e("Error: %s", error)
            _sideEffect.trySend(SideEffect.Toast("Error: $error"))
        }

        override fun onCancelled() {
            Timber.d("Cancelled")
        }
    }

    fun initSdk() {
        if (OpenPad.isInitialized) {
            _state.update { it.copy(sdkState = SdkState.Ready) }
            return
        }
        _state.update { it.copy(sdkState = SdkState.Initializing) }
        val ctx: Context = getApplication()
        val cfg = _state.value.config
        appliedConfig = cfg
        OpenPad.initialize(
            context = ctx,
            config = cfg,
            onReady = { _state.update { it.copy(sdkState = SdkState.Ready) } },
            onError = { error ->
                _state.update { it.copy(sdkState = SdkState.Error) }
                _sideEffect.trySend(SideEffect.Toast("Init failed: $error"))
            }
        )
    }

    private fun ensureConfig() {
        val cfg = _state.value.config
        if (appliedConfig == cfg && OpenPad.isInitialized) return
        OpenPad.release()
        _state.update { it.copy(sdkState = SdkState.Initializing) }
        appliedConfig = cfg
        val ctx: Context = getApplication()
        OpenPad.initialize(
            context = ctx,
            config = cfg,
            onReady = { _state.update { it.copy(sdkState = SdkState.Ready) } },
            onError = { error ->
                _state.update { it.copy(sdkState = SdkState.Error) }
                _sideEffect.trySend(SideEffect.Toast("Init failed: $error"))
            }
        )
    }

    fun onStartVerification() {
        ensureConfig()
        if (_state.value.sdkState != SdkState.Ready) return
        _sideEffect.trySend(SideEffect.LaunchVerification)
    }

    fun onStartHeadless() {
        ensureConfig()
        if (_state.value.sdkState != SdkState.Ready) return
        _sideEffect.trySend(SideEffect.LaunchHeadless(listener))
    }

    fun onConfigChange(config: OpenPadConfig) {
        _state.update { it.copy(config = config) }
    }

    fun onToggleConfig(show: Boolean) {
        _state.update { it.copy(showConfig = show) }
    }

    fun onDismissResult() {
        _state.update { it.copy(showResult = false) }
    }

    fun onResultTapped() {
        _state.update { it.copy(showResult = true) }
    }
}
