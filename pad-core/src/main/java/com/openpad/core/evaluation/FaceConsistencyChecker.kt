package com.openpad.core.evaluation

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.embedding.FaceEmbeddingAnalyzer

/**
 * Checks that the face captured during ANALYZING and CHALLENGE phases
 * belong to the same person, using embedding cosine similarity.
 *
 * Returns false (face swap detected) only when both bitmaps and the
 * embedding analyzer are available and the similarity is below threshold.
 * In all other cases (missing data, model unavailable), returns true
 * to avoid false rejections.
 */
internal object FaceConsistencyChecker {

    private val FULL_BBOX = FaceDetection.BBox(0f, 0f, 1f, 1f)

    fun isConsistent(
        bitmapA: Bitmap?,
        bitmapB: Bitmap?,
        embeddingAnalyzer: FaceEmbeddingAnalyzer?,
        threshold: Float
    ): Boolean {
        if (bitmapA == null || bitmapB == null || embeddingAnalyzer == null) return true
        val pair = embeddingAnalyzer.analyzePair(
            bitmapA, FULL_BBOX, bitmapB, FULL_BBOX
        ) ?: return true
        val embA = pair.first.embedding ?: return true
        val embB = pair.second.embedding ?: return true
        val similarity = embeddingAnalyzer.cosineSimilarity(embA, embB)
        return similarity >= threshold
    }
}
