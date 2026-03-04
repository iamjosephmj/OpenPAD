package com.openpad.core.aggregation

import com.openpad.core.InternalPadConfig
import com.openpad.core.depth.DepthResult
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightedAggregatorTest {

    private val config = InternalPadConfig.Default

    private fun temporal(
        faceDetected: Boolean = true,
        faceConfidence: Float = 0.9f,
        framesCollected: Int = 5,
        consecutiveFaceFrames: Int = 5
    ) = TemporalFeatures(
        faceDetected = faceDetected,
        faceConfidence = faceConfidence,
        faceBboxCenterX = 0.5f,
        faceBboxCenterY = 0.5f,
        faceBboxArea = 0.1f,
        headMovementVariance = 0f,
        faceSizeStability = 0f,
        blinkDetected = false,
        framesCollected = framesCollected,
        frameSimilarity = 0.9f,
        consecutiveFaceFrames = consecutiveFaceFrames,
        movementSmoothness = 0f
    )

    @Test
    fun nullTemporalReturnsAnalyzing() {
        val aggregator = WeightedAggregator(config)
        assertEquals(
            PadStatus.ANALYZING,
            aggregator.classify(null, null, null, null, null, null)
        )
    }

    @Test
    fun insufficientFramesReturnsAnalyzing() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(framesCollected = 1, consecutiveFaceFrames = 5)
        assertEquals(
            PadStatus.ANALYZING,
            aggregator.classify(null, null, null, null, null, features)
        )
    }

    @Test
    fun noFaceReturnsNoFace() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(faceDetected = false)
        assertEquals(
            PadStatus.NO_FACE,
            aggregator.classify(null, null, null, null, null, features)
        )
    }

    @Test
    fun lowFaceConfidenceReturnsNoFace() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(faceConfidence = 0.3f)
        assertEquals(
            PadStatus.NO_FACE,
            aggregator.classify(null, null, null, null, null, features)
        )
    }

    @Test
    fun deviceGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val device = DeviceDetectionResult(
            deviceDetected = true,
            maxConfidence = 0.9f,
            deviceClass = "cell phone",
            overlapWithFace = true,
            spoofScore = 0.8f
        )
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, null, null, device, null, features)
        )
    }

    @Test
    fun textureGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val texture = TextureResult(genuineScore = 0.2f, spoofScore = 0.8f, backgroundScore = 0f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(texture, null, null, null, null, features)
        )
    }

    @Test
    fun cdcnDepthGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val depth = DepthResult.fromBoth(0.8f, 0.2f, cdcnScore = 0.2f, cdcnVariance = 0f, cdcnMean = 0.2f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, depth, null, null, null, features)
        )
    }

    @Test
    fun photometricGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val photometric = PhotometricResult(0.5f, 0.5f, 0.5f, 0.5f, combinedScore = 0.1f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, null, null, null, photometric, features)
        )
    }

    @Test
    fun allGatesPassReturnsLive() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.2f, backgroundScore = 0f)
        val depth = DepthResult.fromBoth(0.8f, 0.2f, cdcnScore = 0.8f, cdcnVariance = 0f, cdcnMean = 0.8f)
        assertEquals(
            PadStatus.LIVE,
            aggregator.classify(texture, depth, null, null, null, features)
        )
    }

    @Test
    fun computeAggregateScoreWithCdcn() {
        val aggregator = WeightedAggregator(config)
        val texture = TextureResult(genuineScore = 1f, spoofScore = 0f, backgroundScore = 0f)
        val depth = DepthResult.fromBoth(1f, 0f, cdcnScore = 1f, cdcnVariance = 0f, cdcnMean = 1f)
        val device = DeviceDetectionResult(false, 0f, null, false, spoofScore = 0f)

        val score = aggregator.computeAggregateScore(texture, depth, device)
        assertEquals(1f, score, 0.01f)
    }

    @Test
    fun computeAggregateScoreWithoutCdcnRedistributesWeight() {
        val aggregator = WeightedAggregator(config)
        val texture = TextureResult(genuineScore = 1f, spoofScore = 0f, backgroundScore = 0f)
        val depth = DepthResult.fromMn3Only(1f, 0f)
        val device = DeviceDetectionResult(false, 0f, null, false, spoofScore = 0f)

        val score = aggregator.computeAggregateScore(texture, depth, device)
        assert(score >= 0.9f && score <= 1.01f)
    }
}
