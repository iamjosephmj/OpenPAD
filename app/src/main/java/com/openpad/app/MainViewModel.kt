package com.openpad.app

import android.content.Context
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadConfig
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadListener
import com.openpad.core.OpenPadResult
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed interface UiState {
        data object Initializing : UiState

        data class Ready(
            val config: OpenPadConfig = OpenPadConfig(),
            val showConfig: Boolean = false,
            val showResult: Boolean = false,
            val lastVerification: VerificationResult? = null
        ) : UiState

        data class Error(val message: String) : UiState
    }

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

    private val _state = MutableStateFlow<UiState>(UiState.Initializing)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _sideEffect = Channel<SideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private var appliedConfig: OpenPadConfig? = null

    private val readyState: UiState.Ready?
        get() = _state.value as? UiState.Ready

    private inline fun updateReady(transform: UiState.Ready.() -> UiState.Ready) {
        _state.update { current ->
            when (current) {
                is UiState.Ready -> current.transform()
                else -> current
            }
        }
    }

    val listener = object : OpenPadListener {
        override fun onLiveConfirmed(result: OpenPadResult) {
            Timber.d("LIVE  confidence=%.2f  duration=%dms", result.confidence, result.durationMs)
            updateReady {
                copy(
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
            updateReady {
                copy(
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
            _sideEffect.trySend(SideEffect.Toast(context.getString(R.string.toast_error, error)))
        }

        override fun onCancelled() {
            Timber.d("Cancelled")
        }
    }

    fun initSdk() {
        if (OpenPad.isInitialized) {
            _state.value = UiState.Ready(config = readyState?.config ?: OpenPadConfig())
            return
        }
        _state.value = UiState.Initializing
        val cfg = readyState?.config ?: OpenPadConfig()
        appliedConfig = cfg
        OpenPad.initialize(
            context = context,
            config = cfg,
            onReady = { _state.value = UiState.Ready(config = cfg) },
            onError = { error ->
                _state.value = UiState.Error(context.getString(R.string.toast_init_failed, error))
                _sideEffect.trySend(SideEffect.Toast(context.getString(R.string.toast_init_failed, error)))
            }
        )
    }

    private fun ensureConfig() {
        val cfg = readyState?.config ?: OpenPadConfig()
        if (appliedConfig == cfg && OpenPad.isInitialized) return
        OpenPad.release()
        _state.value = UiState.Initializing
        appliedConfig = cfg
        OpenPad.initialize(
            context = context,
            config = cfg,
            onReady = { _state.value = UiState.Ready(config = cfg) },
            onError = { error ->
                _state.value = UiState.Error(context.getString(R.string.toast_init_failed, error))
                _sideEffect.trySend(SideEffect.Toast(context.getString(R.string.toast_init_failed, error)))
            }
        )
    }

    fun onStartVerification() {
        ensureConfig()
        if (_state.value !is UiState.Ready) return
        _sideEffect.trySend(SideEffect.LaunchVerification)
    }

    fun onStartHeadless() {
        ensureConfig()
        if (_state.value !is UiState.Ready) return
        _sideEffect.trySend(SideEffect.LaunchHeadless(listener))
    }

    fun onConfigChange(config: OpenPadConfig) {
        updateReady { copy(config = config) }
    }

    fun onToggleConfig(show: Boolean) {
        updateReady { copy(showConfig = show) }
    }

    fun onDismissResult() {
        updateReady { copy(showResult = false) }
    }

    fun onResultTapped() {
        updateReady { copy(showResult = true) }
    }
}
