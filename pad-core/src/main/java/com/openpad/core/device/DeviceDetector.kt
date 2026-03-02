package com.openpad.core.device

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Detects phones, laptops, and screens in the camera frame.
 *
 * A detected device overlapping with the face region is a strong
 * signal of a presentation attack (video replay on phone/laptop).
 */
interface DeviceDetector {
    /**
     * Run device detection on the full camera frame.
     * @param bitmap Full camera frame.
     * @param faceBbox Normalized face bounding box (0..1 coordinates), used for overlap check.
     * @return Detection result with spoof score.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): DeviceDetectionResult

    fun close()
}
