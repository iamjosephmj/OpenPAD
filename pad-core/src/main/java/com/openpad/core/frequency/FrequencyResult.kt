package com.openpad.core.frequency

/**
 * Layer 3 output: frequency-domain analysis for moiré / halftone detection,
 * plus LBP-based screen texture detection.
 *
 * @param moireScore Score indicating screen moiré presence [0..1]. Higher = more likely screen.
 * @param peakFrequency Dominant frequency in the spectrum (cycles per image).
 * @param spectralFlatness Wiener entropy of the spectrum [0..1]. Low = tonal (print/screen).
 * @param lbpScreenScore LBP-based screen score [0..1]. Higher = more likely screen. Works on high-PPI OLED.
 * @param lbpUniformity Ratio of uniform LBP patterns [0..1]. Real skin has higher uniformity.
 * @param lbpChannelCorrelation Inter-channel LBP correlation [0..1]. Screens have higher correlation.
 */
data class FrequencyResult(
    val moireScore: Float,
    val peakFrequency: Float,
    val spectralFlatness: Float,
    val lbpScreenScore: Float = 0f,
    val lbpUniformity: Float = 0f,
    val lbpChannelCorrelation: Float = 0f
)
