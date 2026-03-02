package com.openpad.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.ui.theme.PadColors
import com.openpad.core.ui.viewmodel.VerdictState
import kotlinx.coroutines.delay

@Composable
internal fun VerdictScreen(
    verdictState: VerdictState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    val isLive = verdictState is VerdictState.LiveConfirmed
    val accentColor = if (isLive) PadColors.Success else PadColors.Error

    Surface(
        modifier = modifier.fillMaxSize(),
        color = PadColors.Surface
    ) {
        // Subtle radial gradient
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = size.width * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Shield icon with checkmark or X
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(300)
                ) + fadeIn(tween(300))
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        drawVerdictShield(isLive)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = if (isLive) "Verification Complete" else "Verification Unsuccessful",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PadColors.OnSurfaceHigh,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isLive) {
                    "Your identity has been confirmed."
                } else {
                    val spoof = verdictState as VerdictState.SpoofDetected
                    if (spoof.canRetry) {
                        "We couldn\u2019t confirm your identity.\nAttempt ${spoof.attempt} of ${spoof.maxAttempts}"
                    } else {
                        "We couldn\u2019t confirm your identity.\nMaximum attempts reached."
                    }
                },
                fontSize = 15.sp,
                color = PadColors.OnSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Primary action button
            if (isLive) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PadColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (verdictState is VerdictState.SpoofDetected && verdictState.canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PadColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Try Again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onClose) {
                    Text(
                        "Close",
                        color = PadColors.OnSurface.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                }
            } else {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PadColors.SurfaceVariant,
                        contentColor = PadColors.OnSurface
                    )
                ) {
                    Text("Close", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun DrawScope.drawVerdictShield(isLive: Boolean) {
    val w = size.width
    val h = size.height
    val color = if (isLive) PadColors.Success else PadColors.Error

    // Shield shape
    val shieldPath = Path().apply {
        moveTo(w * 0.5f, h * 0.05f)
        cubicTo(w * 0.5f, h * 0.05f, w * 0.15f, h * 0.08f, w * 0.10f, h * 0.20f)
        lineTo(w * 0.10f, h * 0.50f)
        cubicTo(w * 0.10f, h * 0.70f, w * 0.30f, h * 0.85f, w * 0.50f, h * 0.95f)
        cubicTo(w * 0.70f, h * 0.85f, w * 0.90f, h * 0.70f, w * 0.90f, h * 0.50f)
        lineTo(w * 0.90f, h * 0.20f)
        cubicTo(w * 0.85f, h * 0.08f, w * 0.50f, h * 0.05f, w * 0.50f, h * 0.05f)
        close()
    }

    // Filled shield with low opacity
    drawPath(path = shieldPath, color = color.copy(alpha = 0.10f))

    // Shield outline
    drawPath(
        path = shieldPath,
        color = color,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Inner icon
    val iconStyle = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (isLive) {
        val checkPath = Path().apply {
            moveTo(w * 0.30f, h * 0.48f)
            lineTo(w * 0.44f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.34f)
        }
        drawPath(path = checkPath, color = color, style = iconStyle)
    } else {
        val xPath = Path().apply {
            moveTo(w * 0.33f, h * 0.35f)
            lineTo(w * 0.67f, h * 0.62f)
            moveTo(w * 0.67f, h * 0.35f)
            lineTo(w * 0.33f, h * 0.62f)
        }
        drawPath(path = xPath, color = color, style = iconStyle)
    }
}
