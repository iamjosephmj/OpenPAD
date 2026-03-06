package com.openpad.core.device

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Detects screen-based presentation attack indicators using a custom YOLOv5n model.
 *
 * Detects PAD-specific signals: reflections, screen artifacts, bezels,
 * and finger grips.
 */
interface ScreenReflectionDetector {

    /**
     * Run screen-reflection detection on the full camera frame.
     *
     * @param bitmap Full camera frame.
     * @param faceBbox Normalized face bounding box (0..1 coordinates), used for overlap checks.
     * @return Detection result with per-class flags and a composite spoof score.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): ScreenReflectionResult

    fun close()
}
