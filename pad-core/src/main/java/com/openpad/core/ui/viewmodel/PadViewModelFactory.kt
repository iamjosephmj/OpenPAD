package com.openpad.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadResult
import com.openpad.core.OpenPadThemeConfig
import com.openpad.core.PadPipeline

/**
 * Callback interface for delivering PAD session results.
 * Replaces direct calls to the OpenPad singleton from PadActivity.
 */
internal interface PadSessionCallback {
    fun onResult(result: OpenPadResult)
    fun onError(error: OpenPadError)
    fun onCancelled()
}

/**
 * Factory that injects all dependencies into [PadViewModel], removing
 * the need for the ViewModel to reference the [OpenPad] singleton.
 *
 * Created once per session by [OpenPad.analyze] and passed to [PadActivity].
 */
internal class PadViewModelFactory(
    private val pipeline: PadPipeline,
    private val sessionStartMs: Long,
    private val callback: PadSessionCallback,
    private val theme: OpenPadThemeConfig
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PadViewModel(pipeline, sessionStartMs, callback, theme) as T
    }

    companion object {
        @Volatile
        var pending: PadViewModelFactory? = null
    }
}
