package com.openpad.core.frequency

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/** Layer 3 interface: frequency-domain analysis of face crop. */
interface FrequencyAnalyzer {
    /**
     * Analyze the frequency content of the face region.
     * @param bitmap Full frame.
     * @param faceBbox Normalized face bounding box.
     * @return Frequency analysis result with moiré/halftone scores.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FrequencyResult
}
