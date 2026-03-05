package com.openpad.core.ui.viewmodel

import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengePhase

internal sealed interface PadUiState {

    data object Idle : PadUiState

    data class Active(
        val status: PadStatus = PadStatus.ANALYZING,
        val phase: ChallengePhase = ChallengePhase.ANALYZING,
        val faceBoxWidthFraction: Float = DEFAULT_FACE_BOX_WIDTH_FRACTION,
        val faceBoxHeightFraction: Float = DEFAULT_FACE_BOX_HEIGHT_FRACTION,
        val challengeProgress: Float = 0f,
        val messageOverride: String? = null,
        val lastResult: PadResult? = null
    ) : PadUiState

    data class Done(
        val outcome: PadOutcome,
        val lastResult: PadResult?
    ) : PadUiState

    companion object {
        const val DEFAULT_FACE_BOX_WIDTH_FRACTION = 0.65f
        const val DEFAULT_FACE_BOX_HEIGHT_FRACTION = 0.52f
        const val CHALLENGE_FACE_BOX_WIDTH_FRACTION = 0.88f
        const val CHALLENGE_FACE_BOX_HEIGHT_FRACTION = 0.72f
    }
}
