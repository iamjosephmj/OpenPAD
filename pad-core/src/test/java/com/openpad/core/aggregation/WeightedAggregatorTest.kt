package com.openpad.core.aggregation

import com.openpad.core.InternalPadConfig
import com.openpad.core.depth.DepthResult
import com.openpad.core.device.ScreenReflectionResult
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
        consecutiveFaceFrames: Int = 5,
        headMovementVariance: Float = 0.5f,
        frameSimilarity: Float = 0.9f
    ) = TemporalFeatures(
        faceDetected = faceDetected,
        faceConfidence = faceConfidence,
        faceBboxCenterX = 0.5f,
        faceBboxCenterY = 0.5f,
        faceBboxArea = 0.1f,
        headMovementVariance = headMovementVariance,
        faceSizeStability = 0f,
        blinkDetected = false,
        framesCollected = framesCollected,
        frameSimilarity = frameSimilarity,
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
            aggregator.classify(null, null, null, null, features, null)
        )
    }

    @Test
    fun noFaceReturnsNoFace() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(faceDetected = false)
        assertEquals(
            PadStatus.NO_FACE,
            aggregator.classify(null, null, null, null, features, null)
        )
    }

    @Test
    fun lowFaceConfidenceReturnsNoFace() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(faceConfidence = 0.3f)
        assertEquals(
            PadStatus.NO_FACE,
            aggregator.classify(null, null, null, null, features, null)
        )
    }

    @Test
    fun screenReflectionGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val screen = ScreenReflectionResult(
            reflectionDetected = true,
            artifactDetected = true,
            fingerDetected = false,
            faceOnScreenDetected = false,
            deviceDetected = false,
            maxConfidence = 0.8f,
            spoofSignalCount = 2,
            spoofScore = 0.9f
        )
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, null, null, null, features, screen)
        )
    }

    @Test
    fun textureGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val texture = TextureResult(genuineScore = 0.2f, spoofScore = 0.8f, backgroundScore = 0f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(texture, null, null, null, features, null)
        )
    }

    @Test
    fun cdcnDepthGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val depth = DepthResult.fromBoth(0.8f, 0.2f, cdcnScore = 0.2f, cdcnVariance = 0f, cdcnMean = 0.2f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, depth, null, null, features, null)
        )
    }

    @Test
    fun photometricGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal()
        val photometric = PhotometricResult(0.5f, 0.5f, 0.5f, 0.5f, combinedScore = 0.1f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(null, null, null, photometric, features, null)
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
            aggregator.classify(texture, depth, null, null, features, null)
        )
    }

    @Test
    fun computeAggregateScoreWithCdcn() {
        val aggregator = WeightedAggregator(config)
        val texture = TextureResult(genuineScore = 1f, spoofScore = 0f, backgroundScore = 0f)
        val depth = DepthResult.fromBoth(1f, 0f, cdcnScore = 1f, cdcnVariance = 0f, cdcnMean = 1f)

        val score = aggregator.computeAggregateScore(texture, depth, null)
        assertEquals(1f, score, 0.01f)
    }

    @Test
    fun computeAggregateScoreWithoutCdcnRedistributesWeight() {
        val aggregator = WeightedAggregator(config)
        val texture = TextureResult(genuineScore = 1f, spoofScore = 0f, backgroundScore = 0f)
        val depth = DepthResult.fromMn3Only(1f, 0f)

        val score = aggregator.computeAggregateScore(texture, depth, null)
        assert(score >= 0.9f && score <= 1.01f)
    }

    @Test
    fun staticFrameGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(frameSimilarity = 0.999f)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.2f, backgroundScore = 0f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(texture, null, null, null, features, null)
        )
    }

    @Test
    fun lowMotionGateTriggersSpoofSuspected() {
        val aggregator = WeightedAggregator(config)
        val features = temporal(headMovementVariance = 0.01f, frameSimilarity = 0.8f)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.2f, backgroundScore = 0f)
        assertEquals(
            PadStatus.SPOOF_SUSPECTED,
            aggregator.classify(texture, null, null, null, features, null)
        )
    }
}
