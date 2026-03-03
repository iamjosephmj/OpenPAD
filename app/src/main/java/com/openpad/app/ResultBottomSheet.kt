package com.openpad.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPadResult

private val Blue = Color(0xFF2962FF)
private val DimText = Color(0xFFB0BEC5)
private val Success = Color(0xFF00C853)
private val Error = Color(0xFFD32F2F)

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
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                "Verification Result",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(12.dp))

            // Verdict badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = verdictColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (result.isLive) "LIVE" else "SPOOF",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                    Text(
                        "%.0f%%".format(result.confidence * 100),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                }
            }

            // Core metrics
            SectionLabel("Core Metrics")
            MetricRow("Confidence", "%.2f".format(result.confidence))
            MetricRow("Duration", "%,d ms".format(result.durationMs))
            MetricRow("Spoof Attempts", result.spoofAttempts.toString())

            // 3D Depth characteristics
            val dc = result.depthCharacteristics
            if (dc != null) {
                SectionLabel("3D Depth Characteristics")
                MetricRow("Mean Depth", "%.4f".format(dc.mean))
                MetricRow("Std Deviation", "%.4f".format(dc.standardDeviation))
                MetricRow("Quadrant Variance", "%.6f".format(dc.quadrantVariance))
                MetricRow("Min Depth", "%.4f".format(dc.minDepth))
                MetricRow("Max Depth", "%.4f".format(dc.maxDepth))
                MetricRow("Range", "%.4f".format(dc.maxDepth - dc.minDepth))

                Spacer(Modifier.height(4.dp))
                Text(
                    "Real faces produce higher std dev and quadrant variance " +
                        "due to 3D geometry. Flat spoofs yield near-zero values.",
                    fontSize = 11.sp,
                    color = DimText.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }

            // Checkpoint bitmaps
            val bitmapA = result.faceAtNormalDistance
            val bitmapB = result.faceAtCloseDistance
            if (bitmapA != null || bitmapB != null) {
                SectionLabel("Checkpoint Face Crops")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bitmapA != null) {
                        CheckpointImage(
                            bitmap = bitmapA,
                            label = "Normal Distance",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (bitmapB != null) {
                        CheckpointImage(
                            bitmap = bitmapB,
                            label = "Close Distance",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Face crops captured at challenge checkpoints for " +
                        "consistency verification.",
                    fontSize = 11.sp,
                    color = DimText.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Blue,
        letterSpacing = 0.5.sp
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
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
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = DimText,
            fontWeight = FontWeight.Medium
        )
    }
}
