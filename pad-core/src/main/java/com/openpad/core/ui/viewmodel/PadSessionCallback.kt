package com.openpad.core.ui.viewmodel

import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadResult

/**
 * Callback interface for delivering PAD session results.
 * Used by [PadViewModel] to communicate verdicts back to the SDK layer.
 */
internal interface PadSessionCallback {
    fun onResult(result: OpenPadResult)
    fun onError(error: OpenPadError)
    fun onCancelled()
}
