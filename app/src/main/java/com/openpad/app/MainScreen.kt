package com.openpad.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Accent = Color(0xFF448AFF)
private val AccentBright = Color(0xFF82B1FF)
private val AccentGradientEnd = Color(0xFF69B4FF)
private val BgDark = Color(0xFF080E1A)
private val BgCard = Color(0xFF111B2E)
private val BgCardBorder = Color(0xFF1E2D45)
private val Success = Color(0xFF00E676)
private val Danger = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE8EDF5)
private val TextDim = Color(0xFF6B7D99)

internal enum class HeroVariant { Initializing, Ready, Error }

@Composable
internal fun MainScreen(
    isReady: Boolean,
    isInitializing: Boolean,
    isError: Boolean = false,
    lastVerification: MainViewModel.VerificationResult?,
    onStartVerification: () -> Unit,
    onStartHeadless: () -> Unit,
    onOpenConfig: () -> Unit,
    onResultTapped: () -> Unit
) {
    val heroVariant = when {
        isError -> HeroVariant.Error
        isReady -> HeroVariant.Ready
        else -> HeroVariant.Initializing
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(heroVariant)
    Surface(
        onClick = onOpenConfig,
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder),
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.main_cd_config),
                tint = TextDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
            }

            Spacer(Modifier.weight(1f))

            HeroSection(heroVariant)

            Spacer(Modifier.height(48.dp))

            AnimatedVisibility(
                visible = isInitializing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(3.dp),
                        color = Accent,
                        trackColor = BgCard,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.main_loading),
                        fontSize = 12.sp,
                        color = TextDim,
                        letterSpacing = 1.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = isReady,
                enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.95f),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientButton(
                        onClick = onStartVerification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FrostedActionCard(
                            icon = Icons.Outlined.CameraAlt,
                            label = stringResource(R.string.main_headless),
                            onClick = onStartHeadless,
                            modifier = Modifier.weight(1f)
                        )
                        FrostedActionCard(
                            icon = Icons.Rounded.Settings,
                            label = stringResource(R.string.main_configure),
                            onClick = onOpenConfig,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = lastVerification != null,
                enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                if (lastVerification != null) {
                    ResultCard(lastVerification, onClick = onResultTapped)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = TextDim.copy(alpha = 0.3f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    stringResource(R.string.main_footer),
                    fontSize = 11.sp,
                    color = TextDim.copy(alpha = 0.3f),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AmbientGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(glowAlpha)
            .blur(120.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.28f),
                        radius = size.width * 0.75f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.5f, size.height * 0.28f)
                )
            }
    )
}

@Composable
private fun HeroSection(variant: HeroVariant) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")

    val scanProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )

    val iconColor by animateColorAsState(
        targetValue = when (variant) {
            HeroVariant.Initializing -> Accent.copy(alpha = 0.5f)
            HeroVariant.Ready -> AccentBright
            HeroVariant.Error -> Danger
        },
        animationSpec = tween(600),
        label = "iconColor"
    )

    val ringColor = when (variant) {
        HeroVariant.Ready -> Accent.copy(alpha = ringAlpha)
        HeroVariant.Initializing -> Accent.copy(alpha = ringAlpha * 0.4f)
        HeroVariant.Error -> Danger.copy(alpha = ringAlpha)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.width / 2f
                val innerR = outerR * 0.78f

                drawCircle(
                    color = ringColor,
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = ringColor.copy(alpha = ringColor.alpha * 0.4f),
                    radius = outerR * 0.88f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )

                if (variant == HeroVariant.Ready) {
                    val scanY = cy - innerR + (innerR * 2f * scanProgress)
                    if (scanY in (cy - innerR)..(cy + innerR)) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AccentBright.copy(alpha = 0.5f),
                                    AccentBright.copy(alpha = 0.7f),
                                    AccentBright.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(cx - innerR * 0.7f, scanY),
                            end = Offset(cx + innerR * 0.7f, scanY),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BgCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = iconColor
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            stringResource(R.string.main_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 8.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.main_subtitle),
            fontSize = 13.sp,
            color = TextDim,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Accent, AccentGradientEnd, Accent)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startX = size.width * shimmerOffset,
                            endX = size.width * (shimmerOffset + 0.5f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.main_start_verification),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun FrostedActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(20.dp),
        color = BgCard.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AccentBright
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun StatusChip(variant: HeroVariant) {
    val dotColor by animateColorAsState(
        targetValue = when (variant) {
            HeroVariant.Initializing -> Accent
            HeroVariant.Ready -> Success
            HeroVariant.Error -> Danger
        },
        animationSpec = tween(400),
        label = "dot"
    )

    val label = when (variant) {
        HeroVariant.Initializing -> stringResource(R.string.status_initializing)
        HeroVariant.Ready -> stringResource(R.string.status_ready)
        HeroVariant.Error -> stringResource(R.string.status_error)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(
                        if (variant == HeroVariant.Initializing) pulseAlpha else 1f
                    )
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
internal fun ResultCard(
    verification: MainViewModel.VerificationResult,
    onClick: () -> Unit
) {
    val isLive = verification.isLive
    val accentColor = if (isLive) Success else Danger
    val icon: ImageVector = if (isLive) Icons.Rounded.CheckCircle else Icons.Rounded.Error

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardGlow"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = glowAlpha * 0.3f),
                            accentColor.copy(alpha = glowAlpha),
                            accentColor.copy(alpha = glowAlpha * 0.3f)
                        )
                    ),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = BgCard
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isLive) stringResource(R.string.result_live_confirmed) else stringResource(R.string.result_spoof_detected),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.result_confidence_format, verification.confidence * 100, verification.durationMs),
                    fontSize = 12.sp,
                    color = TextDim
                )
            }
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = stringResource(R.string.main_cd_view_details),
                tint = TextDim.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
