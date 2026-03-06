package com.openpad.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openpad.core.di.PadSessionHolder
import com.openpad.core.ui.theme.PadColors
import com.openpad.core.ui.viewmodel.PadEffect
import com.openpad.core.ui.viewmodel.PadIntent
import com.openpad.core.ui.viewmodel.PadOutcome
import com.openpad.core.ui.viewmodel.PadUiState
import com.openpad.core.ui.viewmodel.PadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors

private const val VERDICT_DISPLAY_MS = 1600L

/**
 * Stateful composable that manages the PAD verification flow.
 *
 * Creates and owns the [PadViewModel], binds the camera, observes
 * UI state, and delegates rendering to the stateless [CameraScreen].
 */
@Composable
internal fun PadRoute(
    factory: PadViewModel.Factory,
    params: PadSessionHolder.Params,
    onDone: (PadViewModel) -> Unit,
    onClose: (PadViewModel) -> Unit
) {
    val viewModel: PadViewModel = viewModel {
        factory.create(params.sessionStartMs, params.callback, params.themeConfig)
    }

    val uiState by viewModel.ui.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        viewModel.initFromSdk()
    }

    val isInitialized = uiState !is PadUiState.Idle
    LaunchedEffect(isInitialized) {
        if (isInitialized) {
            viewModel.dispatch(PadIntent.OnScreenStarted)
            viewModel.bindCamera(context, lifecycleOwner, analysisExecutor)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                PadEffect.CloseRequested -> onClose(viewModel)
                PadEffect.Done, PadEffect.FinishLiveFlow -> {
                    viewModel.finish()
                    delay(VERDICT_DISPLAY_MS)
                    onDone(viewModel)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    when (val state = uiState) {
        is PadUiState.Idle -> { /* waiting for init */ }
        is PadUiState.Active -> {
            CameraScreen(
                surfaceRequest = surfaceRequest,
                status = state.status,
                phase = state.phase,
                faceBoxWidthFraction = state.faceBoxWidthFraction,
                faceBoxHeightFraction = state.faceBoxHeightFraction,
                challengeProgress = state.challengeProgress,
                messageOverride = state.messageOverride,
                onClose = { viewModel.dispatch(PadIntent.OnCloseClicked) }
            )
        }
        is PadUiState.Done -> {
            VerdictOverlay(outcome = state.outcome)
        }
    }
}

@Composable
private fun VerdictOverlay(outcome: PadOutcome) {
    val isLive = outcome is PadOutcome.LiveConfirmed
    val accentColor = if (isLive) PadColors.Success else PadColors.Error
    val label = if (isLive) stringResource(R.string.pad_verdict_verified)
        else stringResource(R.string.pad_verdict_failed)
    val subtitle = if (isLive) stringResource(R.string.pad_verdict_live_subtitle)
        else stringResource(R.string.pad_verdict_spoof_subtitle)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PadColors.Surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    initialScale = 0.5f
                ) + fadeIn(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLive) {
                        AnimatedCheckmark(color = accentColor)
                    } else {
                        AnimatedCross(color = accentColor)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, delayMillis = 200))
            ) {
                Text(
                    text = label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PadColors.OnSurfaceHigh,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300, delayMillis = 350))
            ) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = PadColors.OnSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedCheckmark(color: Color) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, delayMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.size(48.dp)) {
        val strokeWidth = 4.dp.toPx()
        val w = size.width
        val h = size.height

        val p1 = Offset(w * 0.22f, h * 0.52f)
        val p2 = Offset(w * 0.42f, h * 0.72f)
        val p3 = Offset(w * 0.78f, h * 0.30f)

        val totalLen1 = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
        val totalLen2 = kotlin.math.hypot((p3.x - p2.x).toDouble(), (p3.y - p2.y).toDouble()).toFloat()
        val totalLen = totalLen1 + totalLen2
        val drawn = sweep.value * totalLen

        if (drawn > 0f) {
            val seg1Len = drawn.coerceAtMost(totalLen1)
            val frac1 = seg1Len / totalLen1
            val end1 = Offset(
                p1.x + (p2.x - p1.x) * frac1,
                p1.y + (p2.y - p1.y) * frac1
            )
            drawLine(color, p1, end1, strokeWidth, StrokeCap.Round)

            if (drawn > totalLen1) {
                val seg2Len = drawn - totalLen1
                val frac2 = seg2Len / totalLen2
                val end2 = Offset(
                    p2.x + (p3.x - p2.x) * frac2,
                    p2.y + (p3.y - p2.y) * frac2
                )
                drawLine(color, p2, end2, strokeWidth, StrokeCap.Round)
            }
        }

        drawCircle(
            color = color.copy(alpha = 0.25f * sweep.value),
            style = Stroke(width = strokeWidth),
            radius = w * 0.42f
        )
    }
}

@Composable
private fun AnimatedCross(color: Color) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, delayMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.size(48.dp)) {
        val strokeWidth = 4.dp.toPx()
        val w = size.width
        val h = size.height
        val inset = w * 0.28f

        val frac = sweep.value

        drawLine(
            color,
            Offset(inset, inset),
            Offset(inset + (w - 2 * inset) * frac, inset + (h - 2 * inset) * frac),
            strokeWidth,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(w - inset, inset),
            Offset(w - inset - (w - 2 * inset) * frac, inset + (h - 2 * inset) * frac),
            strokeWidth,
            StrokeCap.Round
        )

        drawCircle(
            color = color.copy(alpha = 0.25f * sweep.value),
            style = Stroke(width = strokeWidth),
            radius = w * 0.42f
        )
    }
}

