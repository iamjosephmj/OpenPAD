package com.openpad.core.texture

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/** Layer 2 interface: texture-based face liveness analysis. */
interface TextureAnalyzer {
    /**
     * Analyze texture of the face crop for liveness.
     * @param bitmap Full frame.
     * @param faceBbox Normalized face bounding box.
     * @return Texture analysis result with genuine/spoof scores.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): TextureResult

    fun close()
}
