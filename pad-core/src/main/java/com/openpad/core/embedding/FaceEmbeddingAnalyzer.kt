package com.openpad.core.embedding

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Interface for face embedding extraction.
 *
 * Produces a fixed-dimensional embedding vector from a face crop.
 * Used for face consistency verification during the challenge flow.
 */
interface FaceEmbeddingAnalyzer {
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FaceEmbeddingResult
    fun close()
}
