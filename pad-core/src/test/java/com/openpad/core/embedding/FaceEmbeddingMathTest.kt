package com.openpad.core.embedding

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class FaceEmbeddingMathTest {

    @Test
    fun l2NormalizeProducesUnitVector() {
        val vec = floatArrayOf(3f, 4f)
        val normalized = MobileFaceNetAnalyzer.l2Normalize(vec)
        assertEquals(0.6f, normalized[0], 1e-6f)
        assertEquals(0.8f, normalized[1], 1e-6f)
        val norm = sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1])
        assertEquals(1f, norm, 1e-5f)
    }

    @Test
    fun l2NormalizeZeroVectorReturnsOriginal() {
        val vec = floatArrayOf(0f, 0f, 0f)
        val normalized = MobileFaceNetAnalyzer.l2Normalize(vec)
        assertEquals(0f, normalized[0])
        assertEquals(0f, normalized[1])
        assertEquals(0f, normalized[2])
    }

    @Test
    fun cosineSimilarityIdenticalVectorsReturnsOne() {
        val vec = floatArrayOf(1f, 2f, 3f, 4f)
        val similarity = MobileFaceNetAnalyzer.cosineSimilarity(vec, vec.copyOf())
        assertEquals(1f, similarity, 1e-5f)
    }

    @Test
    fun cosineSimilarityOrthogonalVectorsReturnsZero() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val similarity = MobileFaceNetAnalyzer.cosineSimilarity(a, b)
        assertEquals(0f, similarity, 1e-5f)
    }

    @Test
    fun cosineSimilarityDifferentSizesReturnsZero() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f)
        assertEquals(0f, MobileFaceNetAnalyzer.cosineSimilarity(a, b))
    }
}
