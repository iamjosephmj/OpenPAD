package com.openpad.core.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.openpad.core.R
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.ui.theme.PadColors

@Composable
internal fun CameraScreen(
    surfaceRequest: SurfaceRequest?,
    status: PadStatus,
    phase: ChallengePhase,
    faceBoxWidthFraction: Float,
    faceBoxHeightFraction: Float,
    challengeProgress: Float,
    messageOverride: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedWidth by animateFloatAsState(
        targetValue = faceBoxWidthFraction,
        animationSpec = tween(500),
        label = "faceBoxWidth"
    )
    val animatedHeight by animateFloatAsState(
        targetValue = faceBoxHeightFraction,
        animationSpec = tween(500),
        label = "faceBoxHeight"
    )

    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f),
                contentScale = ContentScale.Crop
            )
        } ?: Surface(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            color = PadColors.Surface
        ) {}

        FaceGuideOverlay(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            status = status,
            phase = phase,
            widthFraction = animatedWidth,
            heightFraction = animatedHeight,
            challengeProgress = challengeProgress
        )

        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = PadColors.FrostGlass.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f))
            ) {
                IconButton(onClick = onClose) {
                    Text(
                        "\u2715",
                        color = PadColors.OnSurface.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            AnimatedContent(
                targetState = phaseTitle(phase, status),
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically { -it / 3 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically { it / 3 })
                },
                modifier = Modifier.align(Alignment.Center),
                label = "phaseTitle"
            ) { title ->
                Text(
                    text = title,
                    color = PadColors.OnSurfaceHigh,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Instruction pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            InstructionPill(
                status = status,
                phase = phase,
                messageOverride = messageOverride
            )
        }
    }
}

@Composable
private fun phaseTitle(phase: ChallengePhase, status: PadStatus): String = when {
    status == PadStatus.LIVE || status == PadStatus.COMPLETED -> stringResource(R.string.pad_phase_verified)
    phase == ChallengePhase.EVALUATING -> stringResource(R.string.pad_phase_evaluating)
    phase == ChallengePhase.CHALLENGE_CLOSER -> stringResource(R.string.pad_phase_move_closer)
    phase == ChallengePhase.POSITIONING -> stringResource(R.string.pad_phase_positioning)
    else -> stringResource(R.string.pad_phase_verifying_identity)
}

@Composable
private fun InstructionPill(
    status: PadStatus,
    phase: ChallengePhase,
    messageOverride: String?
) {
    val message = messageOverride ?: when (status) {
        PadStatus.LIVE, PadStatus.COMPLETED -> null
        PadStatus.NO_FACE -> stringResource(R.string.pad_instruction_position_face)
        PadStatus.SPOOF_SUSPECTED, PadStatus.ANALYZING -> stringResource(R.string.pad_instruction_hold_still_look)
    }

    val pillBorderColor by animateColorAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> PadColors.Success.copy(alpha = 0.25f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(400),
        label = "pillBorder"
    )

    val pillEmoji = when (status) {
        PadStatus.NO_FACE -> "\uD83D\uDC64"
        PadStatus.SPOOF_SUSPECTED -> "\u270B"
        else -> "\u2728"
    }

    AnimatedContent(
        targetState = message,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(200)) + slideOutVertically { -it / 3 })
        },
        label = "instructionPill"
    ) { msg ->
        if (msg != null) {
            Surface(
                color = PadColors.FrostGlass.copy(alpha = 0.70f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, pillBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = pillEmoji,
                        fontSize = 14.sp
                    )
                    Text(
                        text = msg,
                        color = PadColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
