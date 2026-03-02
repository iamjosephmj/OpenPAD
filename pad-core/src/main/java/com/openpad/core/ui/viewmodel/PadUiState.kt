package com.openpad.core.ui.viewmodel

import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengePhase

internal enum class SdkScreen {
    INTRO,
    CAMERA,
    VERDICT
}

internal data class PadUiState(
    val currentScreen: SdkScreen = SdkScreen.INTRO,
    val status: PadStatus = PadStatus.ANALYZING,
    val phase: ChallengePhase = ChallengePhase.IDLE,
    val faceBoxWidthFraction: Float = DEFAULT_FACE_BOX_WIDTH_FRACTION,
    val faceBoxHeightFraction: Float = DEFAULT_FACE_BOX_HEIGHT_FRACTION,
    val challengeProgress: Float = 0f,
    val messageOverride: String? = null,
    val lastResult: PadResult? = null,
    val isInitialized: Boolean = false,
    val verdictState: VerdictState? = null
) {
    companion object {
        const val DEFAULT_FACE_BOX_WIDTH_FRACTION = 0.65f
        const val DEFAULT_FACE_BOX_HEIGHT_FRACTION = 0.52f
        const val CHALLENGE_FACE_BOX_WIDTH_FRACTION = 0.88f
        const val CHALLENGE_FACE_BOX_HEIGHT_FRACTION = 0.72f
    }
}

internal sealed interface VerdictState {
    data object LiveConfirmed : VerdictState
    data class SpoofDetected(
        val attempt: Int,
        val maxAttempts: Int,
        val canRetry: Boolean
    ) : VerdictState
}
