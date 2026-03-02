package com.openpad.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PadConfigTest {

    @Test
    fun defaultValuesMatchExpected() {
        val config = PadConfig.Default

        assertEquals(0.55f, config.minFaceConfidence)
        assertEquals(0.5f, config.textureGenuineThreshold)
        assertEquals(3, config.minFramesForDecision)
        assertEquals(15, config.slidingWindowSize)
        assertEquals(3, config.minConsecutiveFaceFrames)
        assertEquals(2, config.enterConsecutive)
        assertEquals(3, config.exitConsecutive)
        assertEquals(0.5f, config.textureGenuineThreshold)
        assertEquals(0.4f, config.depthFlatnessThreshold)
        assertEquals(0.5f, config.deviceConfidenceThreshold)
        assertEquals(0.6f, config.moireThreshold)
        assertEquals(0.7f, config.lbpScreenThreshold)
        assertEquals(0.30f, config.photometricMinScore)
        assertEquals(0.15f, config.textureWeight)
        assertEquals(0.20f, config.mn3Weight)
        assertEquals(0.55f, config.cdcnWeight)
        assertEquals(0.10f, config.deviceWeight)
        assertEquals(8, config.maxFps)
    }

    @Test
    fun customConfigPreservesValues() {
        val config = PadConfig(
            minFaceConfidence = 0.7f,
            textureGenuineThreshold = 0.6f,
            minFramesForDecision = 5
        )

        assertEquals(0.7f, config.minFaceConfidence)
        assertEquals(0.6f, config.textureGenuineThreshold)
        assertEquals(5, config.minFramesForDecision)
    }
}
