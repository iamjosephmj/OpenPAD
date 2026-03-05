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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Accent = Color(0xFF448AFF)
private val AccentBright = Color(0xFF82B1FF)
private val BgDark = Color(0xFF080E1A)
private val BgCard = Color(0xFF111B2E)
private val BgCardBorder = Color(0xFF1E2D45)
private val Success = Color(0xFF00E676)
private val Danger = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE8EDF5)
private val TextDim = Color(0xFF6B7D99)

internal enum class HeroVariant { Initializing, Ready, Error }

/**
 * Stateless main screen composable. Receives all state as parameters
 * and communicates user actions back via lambda callbacks.
 */
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
                IconButton(onClick = onOpenConfig) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Config",
                        tint = TextDim
                    )
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
                        "Loading models\u2026",
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
                    Button(
                        onClick = onStartVerification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Start Verification",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartHeadless,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BgCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentBright
                            )
                        ) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Headless",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenConfig,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BgCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextDim
                            )
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Configure",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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

            Text(
                "OpenPAD SDK v1.0",
                fontSize = 11.sp,
                color = TextDim.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun AmbientGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.12f,
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
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.7f
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.5f, size.height * 0.3f)
                )
            }
    )
}

@Composable
private fun HeroSection(variant: HeroVariant) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
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
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .border(2.dp, ringColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .border(1.dp, ringColor.copy(alpha = ringColor.alpha * 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(BgCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "OPENPAD",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 6.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Presentation Attack Detection",
            fontSize = 13.sp,
            color = TextDim,
            letterSpacing = 1.sp
        )
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
        HeroVariant.Initializing -> "Initializing"
        HeroVariant.Ready -> "Ready"
        HeroVariant.Error -> "Error"
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
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "cardAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isLive) "Live Confirmed" else "Spoof Detected",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Text(
                    "%.0f%% confidence  \u00b7  %,d ms".format(
                        verification.confidence * 100,
                        verification.durationMs
                    ),
                    fontSize = 12.sp,
                    color = TextDim
                )
            }
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = "View details",
                tint = TextDim.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
