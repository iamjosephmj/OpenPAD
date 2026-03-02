package com.openpad.core.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapConverterTest {

    @Test
    fun computeFrameSimilarityReturnsZeroWhenPrevIsNull() {
        val curr = FloatArray(100) { 0.5f }
        assertEquals(0f, BitmapConverter.computeFrameSimilarity(null, curr))
    }

    @Test
    fun computeFrameSimilarityReturnsZeroWhenSizesMismatch() {
        val prev = FloatArray(50) { 0.5f }
        val curr = FloatArray(100) { 0.5f }
        assertEquals(0f, BitmapConverter.computeFrameSimilarity(prev, curr))
    }

    @Test
    fun computeFrameSimilarityReturnsOneWhenIdentical() {
        val arr = FloatArray(100) { 0.5f }
        assertEquals(1f, BitmapConverter.computeFrameSimilarity(arr, arr.copyOf()))
    }

    @Test
    fun computeFrameSimilarityReturnsLowerForDifferentFrames() {
        val prev = FloatArray(100) { 0.5f }
        val curr = FloatArray(100) { i -> if (i < 50) 0.5f else 0f }
        val similarity = BitmapConverter.computeFrameSimilarity(prev, curr)
        assert(similarity < 1f && similarity >= 0f)
    }
}
