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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
        // Layer 0: Camera preview (background)
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

        // Layer 1: Face guide overlay (scrim + oval + progress)
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

        // Layer 2: Top gradient + frosted bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .align(Alignment.TopCenter)
        ) {
            // Gradient fade from top
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding() + 64.dp
                    )
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to PadColors.Surface.copy(alpha = 0.85f),
                            0.6f to PadColors.Surface.copy(alpha = 0.35f),
                            1.0f to Color.Transparent
                        )
                    )
                )
            }

            // Frosted top bar (positioned over the gradient)
        }

        // The actual top bar content sits on top of the gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(3f)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Close button
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp),
                shape = CircleShape,
                color = PadColors.FrostGlass.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                IconButton(onClick = onClose) {
                    Text(
                        "\u2715",
                        color = PadColors.OnSurface.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                }
            }

            // Animated title based on phase
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

            // Phase step indicator
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhaseStepDot(active = phase.ordinal >= ChallengePhase.ANALYZING.ordinal, color = PadColors.Primary)
                PhaseStepDot(active = phase.ordinal >= ChallengePhase.CHALLENGE_CLOSER.ordinal, color = PadColors.Primary)
                PhaseStepDot(active = phase.ordinal >= ChallengePhase.EVALUATING.ordinal, color = PadColors.PrimaryLight)
            }
        }

        // Layer 4: Bottom gradient + instruction pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .align(Alignment.BottomCenter)
        ) {
            // Gradient fade from bottom
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.5f to PadColors.Surface.copy(alpha = 0.30f),
                            1.0f to PadColors.Surface.copy(alpha = 0.75f)
                        )
                    )
                )
            }
        }

        // Instruction pill on top of gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(3f)
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(bottom = 28.dp),
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
private fun PhaseStepDot(active: Boolean, color: Color) {
    val dotColor by animateColorAsState(
        targetValue = if (active) color else PadColors.OnSurface.copy(alpha = 0.2f),
        animationSpec = tween(400),
        label = "dotColor"
    )
    val dotWidth by animateFloatAsState(
        targetValue = if (active) 16f else 6f,
        animationSpec = tween(350),
        label = "dotWidth"
    )

    Surface(
        modifier = Modifier
            .width(dotWidth.dp)
            .height(6.dp),
        shape = RoundedCornerShape(3.dp),
        color = dotColor
    ) {}
}

private fun phaseTitle(phase: ChallengePhase, status: PadStatus): String = when {
    status == PadStatus.LIVE || status == PadStatus.COMPLETED -> "Verified"
    status == PadStatus.SPOOF_SUSPECTED -> "Try Again"
    phase == ChallengePhase.EVALUATING -> "Evaluating..."
    phase == ChallengePhase.CHALLENGE_CLOSER -> "Move Closer"
    phase == ChallengePhase.POSITIONING -> "Positioning"
    else -> "Verifying Identity"
}

@Composable
private fun InstructionPill(
    status: PadStatus,
    phase: ChallengePhase,
    messageOverride: String?
) {
    val message = messageOverride ?: when (status) {
        PadStatus.LIVE, PadStatus.COMPLETED -> null
        PadStatus.NO_FACE -> "Position your face in the frame"
        PadStatus.SPOOF_SUSPECTED -> "Make sure nothing is covering your face"
        PadStatus.ANALYZING -> "Hold still and look at the camera"
    }

    val pillBorderColor by animateColorAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> PadColors.Success.copy(alpha = 0.25f)
            PadStatus.SPOOF_SUSPECTED -> PadColors.Error.copy(alpha = 0.25f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(400),
        label = "pillBorder"
    )

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
                color = PadColors.FrostGlass.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, pillBorderColor)
            ) {
                Text(
                    text = msg,
                    color = PadColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
