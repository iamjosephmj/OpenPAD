package com.openpad.core.ui.viewmodel

import com.openpad.core.PadResult

internal sealed interface PadIntent {
    data object OnScreenStarted : PadIntent
    data object OnPreviewReady : PadIntent
    data class OnAnalyzerResult(val result: PadResult) : PadIntent
    data object OnCloseClicked : PadIntent
    data object OnScreenDisposed : PadIntent
}

internal sealed interface PadEffect {
    data object FinishLiveFlow : PadEffect
    data object CloseRequested : PadEffect
    data object Done : PadEffect
}

internal sealed interface PadOutcome {
    data object Pending : PadOutcome
    data object LiveConfirmed : PadOutcome
    data class SpoofFailed(val attempts: Int) : PadOutcome
}
