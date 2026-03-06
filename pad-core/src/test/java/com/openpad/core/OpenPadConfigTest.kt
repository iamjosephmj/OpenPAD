package com.openpad.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenPadConfigTest {

    @Test
    fun defaultConfigValues() {
        val config = OpenPadConfig.Default
        assertEquals(0.70f, config.livenessThreshold)
        assertEquals(0.70f, config.faceMatchThreshold)
        assertEquals(0.55f, config.faceDetectionConfidence)
        assertEquals(0.15f, config.textureAnalysisWeight)
        assertEquals(0.20f, config.depthGateWeight)
        assertEquals(0.55f, config.depthAnalysisWeight)
        assertEquals(0.20f, config.depthGateMinScore)
        assertEquals(0.40f, config.depthFlatnessMinScore)
        assertEquals(0.60f, config.moireDetectionThreshold)
        assertEquals(0.70f, config.screenPatternThreshold)
        assertEquals(0.30f, config.photometricMinScore)
        assertEquals(0.08f, config.spoofAttemptPenalty)
        assertEquals(8, config.maxFramesPerSecond)
        assertEquals(false, config.enableDebugOverlay)
        assertEquals(true, config.enablePreprocessing)
        assertEquals(0.45f, config.preprocessingGammaTarget)
        assertEquals(2.0f, config.preprocessingClaheClipLimit)
    }

    @Test
    fun toPadConfigMapsFieldsCorrectly() {
        val config = OpenPadConfig(
            livenessThreshold = 0.80f,
            faceMatchThreshold = 0.75f,
            faceDetectionConfidence = 0.60f,
            textureAnalysisWeight = 0.25f,
            depthGateWeight = 0.15f,
            depthAnalysisWeight = 0.45f,
            depthGateMinScore = 0.30f,
            depthFlatnessMinScore = 0.50f,
            moireDetectionThreshold = 0.65f,
            screenPatternThreshold = 0.75f,
            photometricMinScore = 0.35f,
            spoofAttemptPenalty = 0.10f,
            maxFramesPerSecond = 10
        )
        val padConfig = config.toInternal()
        assertEquals(0.80f, padConfig.genuineProbabilityThreshold)
        assertEquals(0.75f, padConfig.faceConsistencyThreshold)
        assertEquals(0.60f, padConfig.minFaceConfidence)
        assertEquals(0.25f, padConfig.textureWeight)
        assertEquals(0.15f, padConfig.mn3Weight)
        assertEquals(0.45f, padConfig.cdcnWeight)
        assertEquals(0.0f, padConfig.deviceWeight)
        assertEquals(0.30f, padConfig.mn3GateThreshold)
        assertEquals(0.50f, padConfig.depthFlatnessThreshold)
        assertEquals(1.0f, padConfig.deviceConfidenceThreshold)
        assertEquals(0.65f, padConfig.moireThreshold)
        assertEquals(0.75f, padConfig.lbpScreenThreshold)
        assertEquals(0.35f, padConfig.photometricMinScore)
        assertEquals(0.10f, padConfig.spoofAttemptPenaltyPerCount)
        assertEquals(10, padConfig.maxFps)
    }

    @Test
    fun toPadConfigDefaultsMatch() {
        val padConfig = OpenPadConfig.Default.toInternal()
        assertEquals(0.55f, padConfig.minFaceConfidence)
        assertEquals(0.15f, padConfig.textureWeight)
        assertEquals(0.20f, padConfig.mn3Weight)
        assertEquals(0.55f, padConfig.cdcnWeight)
        assertEquals(0.0f, padConfig.deviceWeight)
        assertEquals(8, padConfig.maxFps)
        assertEquals(true, padConfig.enablePreprocessing)
        assertEquals(0.45f, padConfig.preprocessingGammaTarget)
        assertEquals(2.0f, padConfig.preprocessingClaheClipLimit)
    }

    @Test
    fun toPadConfigMapsPreprocessingFields() {
        val config = OpenPadConfig(
            enablePreprocessing = false,
            preprocessingGammaTarget = 0.50f,
            preprocessingClaheClipLimit = 3.0f
        )
        val padConfig = config.toInternal()
        assertEquals(false, padConfig.enablePreprocessing)
        assertEquals(0.50f, padConfig.preprocessingGammaTarget)
        assertEquals(3.0f, padConfig.preprocessingClaheClipLimit)
    }

    @Test
    fun lowEndDevicePresetDisablesPreprocessing() {
        val config = OpenPadConfig.LowEndDevice
        assertEquals(false, config.enablePreprocessing)
    }

    @Test
    fun developmentPresetUsesAggressiveClahe() {
        val config = OpenPadConfig.Development
        assertEquals(true, config.enablePreprocessing)
        assertEquals(3.0f, config.preprocessingClaheClipLimit)
    }
}
