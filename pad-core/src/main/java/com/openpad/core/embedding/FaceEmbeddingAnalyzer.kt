package com.openpad.core.embedding

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Interface for face embedding extraction and comparison.
 *
 * Produces a fixed-dimensional embedding vector from a face crop.
 * Used for face consistency verification during the challenge flow.
 */
interface FaceEmbeddingAnalyzer {
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FaceEmbeddingResult

    /**
     * Analyze two face crops in a single batch inference.
     * Returns a pair of embeddings, or null if the model is unavailable.
     */
    fun analyzePair(
        bitmapA: Bitmap, bboxA: FaceDetection.BBox,
        bitmapB: Bitmap, bboxB: FaceDetection.BBox
    ): Pair<FaceEmbeddingResult, FaceEmbeddingResult>?

    /** Compute cosine similarity between two embedding vectors. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float

    fun close()
}
