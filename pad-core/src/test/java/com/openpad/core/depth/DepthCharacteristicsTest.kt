package com.openpad.core.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DepthCharacteristicsTest {

    @Test
    fun averageReturnsNullForEmptyList() {
        assertNull(DepthCharacteristics.average(emptyList()))
    }

    @Test
    fun averageSingleFrameReturnsSameValues() {
        val single = DepthCharacteristics(
            mean = 0.5f,
            standardDeviation = 0.1f,
            quadrantVariance = 0.02f,
            minDepth = 0.1f,
            maxDepth = 0.9f
        )
        val result = DepthCharacteristics.average(listOf(single))
        assertNotNull(result)
        assertEquals(0.5f, result!!.mean, 1e-6f)
        assertEquals(0.1f, result.standardDeviation, 1e-6f)
        assertEquals(0.02f, result.quadrantVariance, 1e-6f)
        assertEquals(0.1f, result.minDepth, 1e-6f)
        assertEquals(0.9f, result.maxDepth, 1e-6f)
    }

    @Test
    fun averageMultipleFramesComputesCorrectly() {
        val a = DepthCharacteristics(0.4f, 0.1f, 0.01f, 0.1f, 0.8f)
        val b = DepthCharacteristics(0.6f, 0.3f, 0.03f, 0.2f, 1.0f)
        val result = DepthCharacteristics.average(listOf(a, b))!!
        assertEquals(0.5f, result.mean, 1e-6f)
        assertEquals(0.2f, result.standardDeviation, 1e-6f)
        assertEquals(0.02f, result.quadrantVariance, 1e-6f)
        assertEquals(0.15f, result.minDepth, 1e-6f)
        assertEquals(0.9f, result.maxDepth, 1e-6f)
    }

    @Test
    fun fromDepthMapUniformValues() {
        val map = Array(32) { FloatArray(32) { 0.5f } }
        val result = DepthCharacteristics.fromDepthMap(map)
        assertEquals(0.5f, result.mean, 1e-5f)
        assertEquals(0f, result.standardDeviation, 1e-5f)
        assertEquals(0f, result.quadrantVariance, 1e-5f)
        assertEquals(0.5f, result.minDepth, 1e-5f)
        assertEquals(0.5f, result.maxDepth, 1e-5f)
    }

    @Test
    fun fromDepthMapVaryingQuadrants() {
        val map = Array(32) { y ->
            FloatArray(32) { x ->
                when {
                    y < 16 && x < 16 -> 0.2f
                    y < 16 && x >= 16 -> 0.4f
                    y >= 16 && x < 16 -> 0.6f
                    else -> 0.8f
                }
            }
        }
        val result = DepthCharacteristics.fromDepthMap(map)
        assertEquals(0.5f, result.mean, 1e-5f)
        assert(result.quadrantVariance > 0f) { "Quadrant variance should be non-zero" }
        assertEquals(0.2f, result.minDepth, 1e-5f)
        assertEquals(0.8f, result.maxDepth, 1e-5f)
    }

    @Test
    fun fromDepthMapMinMax() {
        val map = Array(32) { FloatArray(32) { 0.5f } }
        map[0][0] = 0.01f
        map[31][31] = 0.99f
        val result = DepthCharacteristics.fromDepthMap(map)
        assertEquals(0.01f, result.minDepth, 1e-5f)
        assertEquals(0.99f, result.maxDepth, 1e-5f)
    }
}
