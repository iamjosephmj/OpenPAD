package com.openpad.core.device

/**
 * Result from YOLOv5-based screen-reflection detection.
 *
 * Detects five classes relevant to screen-based presentation attacks:
 * - **reflection** — specular reflections / bezels on a screen surface
 * - **artifact** — capture artifacts (moiré, color banding) from a screen
 * - **finger** — finger(s) holding a device in front of the camera
 * - **faceOnScreen** — a face displayed on a screen rather than a live person
 * - **device** — the physical device itself (phone, tablet, laptop)
 *
 * A high [spoofSignalCount] (>= 2 spoof-class detections) combined with high
 * [maxConfidence] is a strong indicator of a screen-replay attack.
 *
 * @param reflectionDetected True if screen reflections or bezels were detected.
 * @param artifactDetected True if screen capture artifacts were found.
 * @param fingerDetected True if fingers holding a device were detected.
 * @param faceOnScreenDetected True if a face displayed on a screen was detected.
 * @param deviceDetected True if a physical device was detected.
 * @param maxConfidence Highest confidence across all detections [0..1].
 * @param spoofSignalCount Number of spoof-indicating classes detected (reflection, artifact, finger, device).
 * @param spoofScore Combined spoof score [0..1] derived from detections.
 */
data class ScreenReflectionResult(
    val reflectionDetected: Boolean,
    val artifactDetected: Boolean,
    val fingerDetected: Boolean,
    val faceOnScreenDetected: Boolean,
    val deviceDetected: Boolean,
    val maxConfidence: Float,
    val spoofSignalCount: Int,
    val spoofScore: Float
)
