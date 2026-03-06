package com.openpad.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.openpad.core.OpenPadConfig

private val Blue = Color(0xFF2962FF)
private val BlueDim = Color(0xFF448AFF)
private val DimText = Color(0xFFB0BEC5)
private val BgCard = Color(0xFF111B2E)
private val BgCardBorder = Color(0xFF1E2D45)
private val SheetBg = Color(0xFF1B2838)

private data class PresetInfo(
    val name: String,
    val description: String,
    val config: OpenPadConfig
)

@Composable
private fun buildPresets(deviceConfig: OpenPadConfig): List<PresetInfo> = listOf(
    PresetInfo(stringResource(R.string.preset_auto), stringResource(R.string.preset_auto_desc), deviceConfig),
    PresetInfo(stringResource(R.string.preset_default), stringResource(R.string.preset_default_desc), OpenPadConfig.Default),
    PresetInfo(stringResource(R.string.preset_high_security), stringResource(R.string.preset_high_security_desc), OpenPadConfig.HighSecurity),
    PresetInfo(stringResource(R.string.preset_fast_pass), stringResource(R.string.preset_fast_pass_desc), OpenPadConfig.FastPass),
    PresetInfo(stringResource(R.string.preset_banking), stringResource(R.string.preset_banking_desc), OpenPadConfig.Banking),
    PresetInfo(stringResource(R.string.preset_onboarding), stringResource(R.string.preset_onboarding_desc), OpenPadConfig.Onboarding),
    PresetInfo(stringResource(R.string.preset_kiosk), stringResource(R.string.preset_kiosk_desc), OpenPadConfig.Kiosk),
    PresetInfo(stringResource(R.string.preset_low_end), stringResource(R.string.preset_low_end_desc), OpenPadConfig.LowEndDevice),
    PresetInfo(stringResource(R.string.preset_development), stringResource(R.string.preset_development_desc), OpenPadConfig.Development),
    PresetInfo(stringResource(R.string.preset_high_throughput), stringResource(R.string.preset_high_throughput_desc), OpenPadConfig.HighThroughput),
    PresetInfo(stringResource(R.string.preset_max_accuracy), stringResource(R.string.preset_max_accuracy_desc), OpenPadConfig.MaxAccuracy),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigBottomSheet(
    config: OpenPadConfig,
    onConfigChange: (OpenPadConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val presets = buildPresets(OpenPadConfig.forDevice(context))
    val activePreset = presets.firstOrNull { it.config == config }

    val expandedSections = remember {
        mutableStateMapOf(
            "verdict" to true,
            "weights" to false,
            "model" to false,
            "signal" to false,
            "performance" to true
        )
    }

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
                .padding(bottom = 24.dp)
        ) {
            // Header with active preset badge
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.config_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (activePreset != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Blue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Blue.copy(alpha = 0.3f))
                        ) {
                            Text(
                                activePreset.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BlueDim,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.config_subtitle),
                    fontSize = 13.sp,
                    color = DimText
                )
            }

            // Preset cards in horizontal scroll
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets) { preset ->
                    val selected = preset == activePreset
                    PresetCard(
                        name = preset.name,
                        description = preset.description,
                        selected = selected,
                        onClick = { onConfigChange(preset.config) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Collapsible sections in cards
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                CollapsibleSection(
                    title = stringResource(R.string.section_verdict),
                    expanded = expandedSections["verdict"] == true,
                    onToggle = { expandedSections["verdict"] = it }
                ) {
                    SliderRow(stringResource(R.string.slider_liveness_threshold), config.livenessThreshold, 0f, 1f, stringResource(R.string.range_lenient), stringResource(R.string.range_strict)) {
                        onConfigChange(config.copy(livenessThreshold = it))
                    }
                    SliderRow(stringResource(R.string.slider_face_match_threshold), config.faceMatchThreshold, 0f, 1f, stringResource(R.string.range_lenient), stringResource(R.string.range_strict)) {
                        onConfigChange(config.copy(faceMatchThreshold = it))
                    }
                    SliderRow(stringResource(R.string.slider_face_detection_confidence), config.faceDetectionConfidence, 0f, 1f, stringResource(R.string.range_low), stringResource(R.string.range_high)) {
                        onConfigChange(config.copy(faceDetectionConfidence = it))
                    }
                }

                Spacer(Modifier.height(10.dp))

                CollapsibleSection(
                    title = stringResource(R.string.section_scoring_weights),
                    expanded = expandedSections["weights"] == true,
                    onToggle = { expandedSections["weights"] = it }
                ) {
                    SliderRow(stringResource(R.string.slider_texture_analysis), config.textureAnalysisWeight, 0f, 1f) {
                        onConfigChange(config.copy(textureAnalysisWeight = it))
                    }
                    SliderRow(stringResource(R.string.slider_depth_gate), config.depthGateWeight, 0f, 1f) {
                        onConfigChange(config.copy(depthGateWeight = it))
                    }
                    SliderRow(stringResource(R.string.slider_depth_map), config.depthAnalysisWeight, 0f, 1f) {
                        onConfigChange(config.copy(depthAnalysisWeight = it))
                    }
                }

                Spacer(Modifier.height(10.dp))

                CollapsibleSection(
                    title = stringResource(R.string.section_model_thresholds),
                    expanded = expandedSections["model"] == true,
                    onToggle = { expandedSections["model"] = it }
                ) {
                    SliderRow(stringResource(R.string.slider_depth_gate_min), config.depthGateMinScore, 0f, 1f) {
                        onConfigChange(config.copy(depthGateMinScore = it))
                    }
                    SliderRow(stringResource(R.string.slider_depth_flatness_min), config.depthFlatnessMinScore, 0f, 1f) {
                        onConfigChange(config.copy(depthFlatnessMinScore = it))
                    }
                }

                Spacer(Modifier.height(10.dp))

                CollapsibleSection(
                    title = stringResource(R.string.section_signal_thresholds),
                    expanded = expandedSections["signal"] == true,
                    onToggle = { expandedSections["signal"] = it }
                ) {
                    SliderRow(stringResource(R.string.slider_moire_detection), config.moireDetectionThreshold, 0f, 1f) {
                        onConfigChange(config.copy(moireDetectionThreshold = it))
                    }
                    SliderRow(stringResource(R.string.slider_screen_pattern), config.screenPatternThreshold, 0f, 1f) {
                        onConfigChange(config.copy(screenPatternThreshold = it))
                    }
                    SliderRow(stringResource(R.string.slider_photometric_min), config.photometricMinScore, 0f, 1f) {
                        onConfigChange(config.copy(photometricMinScore = it))
                    }
                    SliderRow(stringResource(R.string.slider_spoof_penalty), config.spoofAttemptPenalty, 0f, 0.5f) {
                        onConfigChange(config.copy(spoofAttemptPenalty = it))
                    }
                }

                Spacer(Modifier.height(10.dp))

                CollapsibleSection(
                    title = stringResource(R.string.section_performance),
                    expanded = expandedSections["performance"] == true,
                    onToggle = { expandedSections["performance"] = it }
                ) {
                    IntSliderRow(stringResource(R.string.slider_max_fps), config.maxFramesPerSecond, 1, 30) {
                        onConfigChange(config.copy(maxFramesPerSecond = it))
                    }
                    ToggleRow(stringResource(R.string.toggle_debug_overlay), config.enableDebugOverlay) {
                        onConfigChange(config.copy(enableDebugOverlay = it))
                    }
                    ToggleRow(stringResource(R.string.toggle_frame_enhancement), config.enableFrameEnhancement) {
                        onConfigChange(config.copy(enableFrameEnhancement = it))
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { onConfigChange(OpenPadConfig.Default) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, BgCardBorder)
                ) {
                    Text(stringResource(R.string.config_reset), color = Blue, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PresetCard(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Blue.copy(alpha = 0.15f) else BgCard,
        border = BorderStroke(
            1.dp,
            if (selected) Blue.copy(alpha = 0.5f) else BgCardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) BlueDim else Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                description,
                fontSize = 10.sp,
                color = if (selected) Blue.copy(alpha = 0.7f) else DimText.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder),
        modifier = Modifier.animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(!expanded) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Blue,
                    letterSpacing = 0.5.sp
                )
                Text(
                    if (expanded) "\u25B2" else "\u25BC",
                    fontSize = 10.sp,
                    color = DimText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    )
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    rangeStart: String? = null,
    rangeEnd: String? = null,
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
                inactiveTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
        if (rangeStart != null && rangeEnd != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rangeStart, fontSize = 10.sp, color = DimText.copy(alpha = 0.5f))
                Text(rangeEnd, fontSize = 10.sp, color = DimText.copy(alpha = 0.5f))
            }
        }
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
                inactiveTrackColor = Color.White.copy(alpha = 0.08f)
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
