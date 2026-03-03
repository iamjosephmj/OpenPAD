package com.openpad.core.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthResultTest {

    @Test
    fun fromMn3OnlySetsCorrectFields() {
        val result = DepthResult.fromMn3Only(mn3Real = 0.8f, mn3Spoof = 0.2f)
        assertEquals(0.8f, result.mn3RealScore)
        assertEquals(0.2f, result.mn3SpoofScore)
        assertNull(result.cdcnDepthScore)
        assertFalse(result.cdcnAvailable)
        assertEquals(0.8f, result.depthScore)
    }

    @Test
    fun fromBothSetsCdcnFields() {
        val result = DepthResult.fromBoth(
            mn3Real = 0.7f,
            mn3Spoof = 0.3f,
            cdcnScore = 0.85f,
            cdcnVariance = 0.02f,
            cdcnMean = 0.6f
        )
        assertEquals(0.7f, result.mn3RealScore)
        assertEquals(0.3f, result.mn3SpoofScore)
        assertEquals(0.85f, result.cdcnDepthScore!!, 0f)
        assertTrue(result.cdcnAvailable)
        assertTrue(result.cdcnTriggered)
        assertEquals(0.85f, result.depthScore)
    }

    @Test
    fun fromBothWithCharacteristicsPreservesThem() {
        val chars = DepthCharacteristics(
            mean = 0.5f,
            standardDeviation = 0.1f,
            quadrantVariance = 0.01f,
            minDepth = 0.2f,
            maxDepth = 0.9f
        )
        val result = DepthResult.fromBoth(
            mn3Real = 0.8f,
            mn3Spoof = 0.2f,
            cdcnScore = 0.9f,
            cdcnVariance = 0.01f,
            cdcnMean = 0.5f,
            characteristics = chars
        )
        assertEquals(chars, result.depthCharacteristics)
    }
}
