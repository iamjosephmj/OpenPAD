package com.openpad.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.ui.theme.PadColors

@Composable
internal fun FaceGuideOverlay(
    modifier: Modifier = Modifier,
    status: PadStatus,
    phase: ChallengePhase,
    widthFraction: Float,
    heightFraction: Float,
    challengeProgress: Float = 0f
) {
    val isActive = phase == ChallengePhase.ANALYZING ||
        phase == ChallengePhase.CHALLENGE_CLOSER ||
        phase == ChallengePhase.POSITIONING

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> PadColors.Success
            PadStatus.SPOOF_SUSPECTED -> PadColors.Error
            PadStatus.NO_FACE -> PadColors.OnSurface.copy(alpha = 0.2f)
            PadStatus.ANALYZING -> PadColors.OvalIdle
        },
        animationSpec = tween(500),
        label = "borderColor"
    )

    val arcColor by animateColorAsState(
        targetValue = when {
            challengeProgress > 0.8f -> PadColors.Success
            challengeProgress > 0f -> PadColors.Primary
            else -> PadColors.PrimaryLight
        },
        animationSpec = tween(400),
        label = "arcColor"
    )

    val glowColor by animateColorAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> PadColors.Success
            PadStatus.SPOOF_SUSPECTED -> PadColors.Error
            else -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "glowColor"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> 0.18f
            PadStatus.SPOOF_SUSPECTED -> 0.14f
            else -> 0f
        },
        animationSpec = tween(500),
        label = "glowAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "guidePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val borderAlpha = if (isActive) pulseAlpha else 0.35f

    val animatedProgress by animateFloatAsState(
        targetValue = challengeProgress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "progressArc"
    )

    val borderWidth by animateFloatAsState(
        targetValue = when (status) {
            PadStatus.LIVE, PadStatus.COMPLETED -> 3f
            PadStatus.SPOOF_SUSPECTED -> 2.5f
            else -> 1.5f
        },
        animationSpec = tween(400),
        label = "borderWidth"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height
        val centerX = canvasW / 2f
        val centerY = canvasH / 2f

        val rx = canvasW * widthFraction / 2f
        val ry = canvasH * heightFraction / 2f

        val ovalRect = Rect(centerX - rx, centerY - ry, centerX + rx, centerY + ry)

        // Scrim: radial gradient for softer falloff around the oval
        val cutoutPath = Path().apply { addOval(ovalRect) }
        clipPath(cutoutPath, ClipOp.Difference) {
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.45f to PadColors.Scrim.copy(alpha = 0.40f),
                        0.65f to PadColors.Scrim.copy(alpha = 0.65f),
                        1.0f to PadColors.Scrim.copy(alpha = 0.80f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxOf(canvasW, canvasH) * 0.55f
                )
            )
        }

        // Outer glow ring (animated per status)
        if (glowAlpha > 0.01f) {
            drawOval(
                color = glowColor.copy(alpha = glowAlpha),
                topLeft = Offset(ovalRect.left - 10f, ovalRect.top - 10f),
                size = Size(ovalRect.width + 20f, ovalRect.height + 20f),
                style = Stroke(width = 14f)
            )
        }

        // Oval border
        drawOval(
            color = borderColor.copy(alpha = borderAlpha),
            topLeft = Offset(ovalRect.left, ovalRect.top),
            size = Size(ovalRect.width, ovalRect.height),
            style = Stroke(width = borderWidth)
        )

        // Progress arc
        if (animatedProgress > 0f) {
            val arcInset = 5f
            val arcRect = Rect(
                ovalRect.left - arcInset,
                ovalRect.top - arcInset,
                ovalRect.right + arcInset,
                ovalRect.bottom + arcInset
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(arcRect.left, arcRect.top),
                size = Size(arcRect.width, arcRect.height),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }

        // Corner accent brackets for a polished look
        val bracketLen = 24f
        val bracketStroke = Stroke(width = 2.5f, cap = StrokeCap.Round)
        val bracketColor = borderColor.copy(alpha = borderAlpha * 0.7f)
        val bPad = 18f

        // Top-left
        drawLine(bracketColor, Offset(ovalRect.left - bPad, ovalRect.top + bracketLen), Offset(ovalRect.left - bPad, ovalRect.top - bPad), bracketStroke.width)
        drawLine(bracketColor, Offset(ovalRect.left - bPad, ovalRect.top - bPad), Offset(ovalRect.left + bracketLen, ovalRect.top - bPad), bracketStroke.width)

        // Top-right
        drawLine(bracketColor, Offset(ovalRect.right + bPad, ovalRect.top + bracketLen), Offset(ovalRect.right + bPad, ovalRect.top - bPad), bracketStroke.width)
        drawLine(bracketColor, Offset(ovalRect.right + bPad, ovalRect.top - bPad), Offset(ovalRect.right - bracketLen, ovalRect.top - bPad), bracketStroke.width)

        // Bottom-left
        drawLine(bracketColor, Offset(ovalRect.left - bPad, ovalRect.bottom - bracketLen), Offset(ovalRect.left - bPad, ovalRect.bottom + bPad), bracketStroke.width)
        drawLine(bracketColor, Offset(ovalRect.left - bPad, ovalRect.bottom + bPad), Offset(ovalRect.left + bracketLen, ovalRect.bottom + bPad), bracketStroke.width)

        // Bottom-right
        drawLine(bracketColor, Offset(ovalRect.right + bPad, ovalRect.bottom - bracketLen), Offset(ovalRect.right + bPad, ovalRect.bottom + bPad), bracketStroke.width)
        drawLine(bracketColor, Offset(ovalRect.right + bPad, ovalRect.bottom + bPad), Offset(ovalRect.right - bracketLen, ovalRect.bottom + bPad), bracketStroke.width)
    }
}
