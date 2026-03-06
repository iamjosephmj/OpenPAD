package com.openpad.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openpad.core.R
import com.openpad.core.ui.theme.PadColors
import kotlinx.coroutines.delay

@Composable
internal fun IntroScreen(
    onBeginVerification: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        appeared = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600),
        label = "introFade"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = PadColors.Surface
    ) {
        // Subtle radial gradient background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PadColors.Primary.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = size.width * 0.8f
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

            // Shield icon
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                ShieldIcon(
                    modifier = Modifier.size(100.dp),
                    alpha = contentAlpha
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.pad_intro_title),
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = PadColors.OnSurfaceHigh.copy(alpha = contentAlpha)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.pad_intro_subtitle),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = PadColors.OnSurface.copy(alpha = contentAlpha * 0.7f),
                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onBeginVerification,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PadColors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.pad_intro_begin),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShieldIcon(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val pulse = rememberInfiniteTransition(label = "shieldPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer glow
        drawShieldPath(
            width = w,
            height = h,
            color = PadColors.Primary.copy(alpha = glowAlpha * alpha),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Shield outline
        drawShieldPath(
            width = w,
            height = h,
            color = PadColors.Primary.copy(alpha = alpha),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Checkmark inside
        val checkPath = Path().apply {
            moveTo(w * 0.30f, h * 0.48f)
            lineTo(w * 0.44f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.34f)
        }
        drawPath(
            path = checkPath,
            color = PadColors.Primary.copy(alpha = alpha),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawShieldPath(
    width: Float,
    height: Float,
    color: Color,
    style: Stroke
) {
    val path = Path().apply {
        moveTo(width * 0.5f, height * 0.05f)
        // Top-left curve
        cubicTo(
            width * 0.5f, height * 0.05f,
            width * 0.15f, height * 0.08f,
            width * 0.10f, height * 0.20f
        )
        // Left side
        lineTo(width * 0.10f, height * 0.50f)
        // Bottom-left curve
        cubicTo(
            width * 0.10f, height * 0.70f,
            width * 0.30f, height * 0.85f,
            width * 0.50f, height * 0.95f
        )
        // Bottom-right curve
        cubicTo(
            width * 0.70f, height * 0.85f,
            width * 0.90f, height * 0.70f,
            width * 0.90f, height * 0.50f
        )
        // Right side
        lineTo(width * 0.90f, height * 0.20f)
        // Top-right curve
        cubicTo(
            width * 0.85f, height * 0.08f,
            width * 0.50f, height * 0.05f,
            width * 0.50f, height * 0.05f
        )
        close()
    }
    drawPath(path = path, color = color, style = style)
}
