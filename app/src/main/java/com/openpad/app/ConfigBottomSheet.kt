package com.openpad.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPadConfig

private val Blue = Color(0xFF2962FF)
private val DimText = Color(0xFFB0BEC5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigBottomSheet(
    config: OpenPadConfig,
    onConfigChange: (OpenPadConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Text(
                "Configuration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Changes apply on the next verification run.",
                fontSize = 13.sp,
                color = DimText
            )

            // --- Verdict ---
            SectionHeader("Verdict")
            SliderRow("Liveness Threshold", config.livenessThreshold, 0f, 1f) {
                onConfigChange(config.copy(livenessThreshold = it))
            }
            SliderRow("Face Match Threshold", config.faceMatchThreshold, 0f, 1f) {
                onConfigChange(config.copy(faceMatchThreshold = it))
            }
            SliderRow("Face Detection Confidence", config.faceDetectionConfidence, 0f, 1f) {
                onConfigChange(config.copy(faceDetectionConfidence = it))
            }

            // --- Scoring Weights ---
            SectionHeader("Scoring Weights")
            SliderRow("Texture Analysis", config.textureAnalysisWeight, 0f, 1f) {
                onConfigChange(config.copy(textureAnalysisWeight = it))
            }
            SliderRow("Depth Gate (MN3)", config.depthGateWeight, 0f, 1f) {
                onConfigChange(config.copy(depthGateWeight = it))
            }
            SliderRow("Depth Map (CDCN)", config.depthAnalysisWeight, 0f, 1f) {
                onConfigChange(config.copy(depthAnalysisWeight = it))
            }
            SliderRow("Screen Detection", config.screenDetectionWeight, 0f, 1f) {
                onConfigChange(config.copy(screenDetectionWeight = it))
            }

            // --- Model Thresholds ---
            SectionHeader("Model Thresholds")
            SliderRow("Depth Gate Min Score", config.depthGateMinScore, 0f, 1f) {
                onConfigChange(config.copy(depthGateMinScore = it))
            }
            SliderRow("Depth Flatness Min", config.depthFlatnessMinScore, 0f, 1f) {
                onConfigChange(config.copy(depthFlatnessMinScore = it))
            }
            SliderRow("Screen Detection Min Confidence", config.screenDetectionMinConfidence, 0f, 1f) {
                onConfigChange(config.copy(screenDetectionMinConfidence = it))
            }

            // --- Signal Thresholds ---
            SectionHeader("Signal Thresholds")
            SliderRow("Moire Detection", config.moireDetectionThreshold, 0f, 1f) {
                onConfigChange(config.copy(moireDetectionThreshold = it))
            }
            SliderRow("Screen Pattern (LBP)", config.screenPatternThreshold, 0f, 1f) {
                onConfigChange(config.copy(screenPatternThreshold = it))
            }
            SliderRow("Photometric Min Score", config.photometricMinScore, 0f, 1f) {
                onConfigChange(config.copy(photometricMinScore = it))
            }
            SliderRow("Spoof Attempt Penalty", config.spoofAttemptPenalty, 0f, 0.5f) {
                onConfigChange(config.copy(spoofAttemptPenalty = it))
            }

            // --- Performance ---
            SectionHeader("Performance")
            IntSliderRow("Max FPS", config.maxFramesPerSecond, 1, 30) {
                onConfigChange(config.copy(maxFramesPerSecond = it))
            }
            ToggleRow("Debug Overlay", config.enableDebugOverlay) {
                onConfigChange(config.copy(enableDebugOverlay = it))
            }
            ToggleRow("Frame Enhancement (ESPCN)", config.enableFrameEnhancement) {
                onConfigChange(config.copy(enableFrameEnhancement = it))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { onConfigChange(OpenPadConfig()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset to Defaults", color = Blue)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(20.dp))
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
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Text(
                "%.2f".format(value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Blue
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Blue,
                activeTrackColor = Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Text(
                value.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Blue
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            colors = SliderDefaults.colors(
                thumbColor = Blue,
                activeTrackColor = Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Blue,
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
