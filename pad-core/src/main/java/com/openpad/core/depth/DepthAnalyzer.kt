package com.openpad.core.depth

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Depth-based face liveness analysis interface.
 *
 * Implementations analyze a face crop and produce a depth map score
 * indicating whether the face has real 3D structure or is flat (spoof).
 */
interface DepthAnalyzer {
    /**
     * Analyze the depth characteristics of the face region.
     * @param bitmap Full camera frame.
     * @param faceBbox Normalized face bounding box (0..1 coordinates).
     * @return Depth analysis result.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): DepthResult

    fun close()
}
