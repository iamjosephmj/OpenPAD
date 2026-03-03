package com.openpad.core.ui.viewmodel

internal sealed class VerdictState {
    data object LiveConfirmed : VerdictState()
    data class SpoofDetected(
        val canRetry: Boolean,
        val attempt: Int,
        val maxAttempts: Int
    ) : VerdictState()
}
