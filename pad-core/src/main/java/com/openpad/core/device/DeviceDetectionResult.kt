package com.openpad.core.device

/**
 * Result from device/screen detection in the camera frame.
 *
 * Detects phones, laptops, and TVs/monitors that could indicate
 * a presentation attack (someone showing a face on a screen).
 *
 * @param deviceDetected Whether any device (phone/laptop/tv) was detected.
 * @param maxConfidence Highest confidence among detected devices [0..1].
 * @param deviceClass Label of the most confident detection (e.g. "cell phone", "tv", "laptop").
 * @param overlapWithFace Whether the detected device bounding box overlaps with the face bounding box.
 * @param spoofScore Combined spoof signal [0..1]. High = strong evidence of replay attack.
 */
data class DeviceDetectionResult(
    val deviceDetected: Boolean,
    val maxConfidence: Float,
    val deviceClass: String?,
    val overlapWithFace: Boolean,
    val spoofScore: Float
)
