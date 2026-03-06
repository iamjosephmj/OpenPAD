package com.openpad.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPadResult

private val Blue = Color(0xFF2962FF)
private val DimText = Color(0xFFB0BEC5)
private val Success = Color(0xFF00C853)
private val Error = Color(0xFFD32F2F)
private val BgCard = Color(0xFF111B2E)
private val BgCardBorder = Color(0xFF1E2D45)
private val SheetBg = Color(0xFF1B2838)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultBottomSheet(
    result: OpenPadResult,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val verdictColor = if (result.isLive) Success else Error

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.result_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(16.dp))

            // Verdict with confidence gauge
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = verdictColor.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, verdictColor.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Confidence gauge arc
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val strokeW = 5f
                            val arcSize = Size(size.width - strokeW, size.height - strokeW)
                            val topLeft = Offset(strokeW / 2f, strokeW / 2f)

                            drawArc(
                                color = verdictColor.copy(alpha = 0.15f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = verdictColor,
                                startAngle = 135f,
                                sweepAngle = 270f * result.confidence,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            stringResource(R.string.result_confidence_pct, result.confidence * 100),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = verdictColor
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (result.isLive) stringResource(R.string.result_live) else stringResource(R.string.result_spoof),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = verdictColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (result.isLive) stringResource(R.string.result_liveness_confirmed) else stringResource(R.string.result_attack_suspected),
                            fontSize = 13.sp,
                            color = DimText
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Metric cards in a grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    label = stringResource(R.string.metric_duration),
                    value = stringResource(R.string.result_duration_format, result.durationMs),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = stringResource(R.string.metric_confidence),
                    value = stringResource(R.string.result_confidence_value, result.confidence),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = stringResource(R.string.metric_attempts),
                    value = result.spoofAttempts.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Depth characteristics
            val dc = result.depthCharacteristics
            if (dc != null) {
                SectionLabel(stringResource(R.string.section_depth_analysis))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = BgCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BgCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Depth bar chart
                        DepthBarChart(
                            mean = dc.mean.toFloat(),
                            stdDev = dc.standardDeviation.toFloat(),
                            min = dc.minDepth.toFloat(),
                            max = dc.maxDepth.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DepthStat(stringResource(R.string.depth_mean), "%.4f".format(dc.mean))
                            DepthStat(stringResource(R.string.depth_std_dev), "%.4f".format(dc.standardDeviation))
                            DepthStat(stringResource(R.string.depth_qv), "%.5f".format(dc.quadrantVariance))
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.depth_explanation),
                            fontSize = 10.sp,
                            color = DimText.copy(alpha = 0.5f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Checkpoint face crops
            val bitmapA = result.faceAtNormalDistance
            val bitmapB = result.faceAtCloseDistance
            if (bitmapA != null || bitmapB != null) {
                SectionLabel(stringResource(R.string.section_face_checkpoints))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmapA != null) {
                        CheckpointImage(
                            bitmap = bitmapA,
                            label = stringResource(R.string.checkpoint_normal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (bitmapA != null && bitmapB != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(32.dp)
                        ) {
                            Text(
                                "\u2194",
                                fontSize = 18.sp,
                                color = if (result.isLive) Success.copy(alpha = 0.6f) else Error.copy(alpha = 0.6f)
                            )
                            Text(
                                if (result.isLive) "\u2713" else "\u2717",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (result.isLive) Success else Error
                            )
                        }
                    }

                    if (bitmapB != null) {
                        CheckpointImage(
                            bitmap = bitmapB,
                            label = stringResource(R.string.checkpoint_close),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = DimText,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DepthBarChart(
    mean: Float,
    stdDev: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier
) {
    val barColor = Blue
    val values = listOf(
        "Min" to min,
        "Mean" to mean,
        "Max" to max,
        "StdDev" to stdDev
    )
    val maxVal = values.maxOf { it.second }.coerceAtLeast(0.001f)

    Canvas(modifier = modifier) {
        val barCount = values.size
        val gap = 12f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val maxHeight = size.height * 0.75f

        values.forEachIndexed { i, (_, v) ->
            val x = i * (barWidth + gap)
            val barH = (v / maxVal) * maxHeight
            val topY = size.height - barH

            drawRoundRect(
                color = barColor.copy(alpha = 0.15f),
                topLeft = Offset(x, size.height - maxHeight),
                size = Size(barWidth, maxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
            drawRoundRect(
                color = barColor.copy(alpha = 0.7f),
                topLeft = Offset(x, topY),
                size = Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
        }
    }
}

@Composable
private fun DepthStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = DimText.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Blue,
        letterSpacing = 0.5.sp
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun CheckpointImage(
    bitmap: Bitmap,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = DimText,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
