package com.openpad.core.aggregation

import com.openpad.core.PadConfig
import com.openpad.core.depth.DepthResult
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WeightedAggregatorTest {

    private lateinit var aggregator: WeightedAggregator
    private val config = PadConfig.Default

    @Before
    fun setup() {
        aggregator = WeightedAggregator(config)
    }

    @Test
    fun `returns ANALYZING when no temporal features`() {
        val status = aggregator.classify(null, null, null, null, null, null)
        assertEquals(PadStatus.ANALYZING, status)
    }

    @Test
    fun `returns ANALYZING when insufficient frames`() {
        val features = createFeatures(framesCollected = 5)
        val status = aggregator.classify(null, null, null, null, null, features)
        assertEquals(PadStatus.ANALYZING, status)
    }

    @Test
    fun `returns NO_FACE when face not detected`() {
        val features = createFeatures(faceDetected = false, framesCollected = 15)
        val status = aggregator.classify(null, null, null, null, null, features)
        assertEquals(PadStatus.NO_FACE, status)
    }

    @Test
    fun `returns NO_FACE when low confidence`() {
        val features = createFeatures(faceConfidence = 0.5f, framesCollected = 15)
        val status = aggregator.classify(null, null, null, null, null, features)
        assertEquals(PadStatus.NO_FACE, status)
    }

    @Test
    fun `returns ANALYZING when insufficient consecutive frames`() {
        val features = createFeatures(consecutiveFaceFrames = 3, framesCollected = 15)
        val status = aggregator.classify(null, null, null, null, null, features)
        assertEquals(PadStatus.ANALYZING, status)
    }

    @Test
    fun `returns SPOOF_SUSPECTED when texture gate fails`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.3f, spoofScore = 0.6f, backgroundScore = 0.1f)
        val status = aggregator.classify(texture, null, null, null, null, features)
        assertEquals(PadStatus.SPOOF_SUSPECTED, status)
    }

    @Test
    fun `returns LIVE when MN3 is low but texture passes (MN3 not a hard gate)`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val depth = DepthResult.fromMn3Only(mn3Real = 0.3f, mn3Spoof = 0.7f)
        val status = aggregator.classify(texture, depth, null, null, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns SPOOF_SUSPECTED when CDCN gate fails`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val depth = DepthResult.fromBoth(
            mn3Real = 0.9f, mn3Spoof = 0.1f,
            cdcnScore = 0.2f, cdcnVariance = 0.01f, cdcnMean = 0.2f
        )
        val status = aggregator.classify(texture, depth, null, null, null, features)
        assertEquals(PadStatus.SPOOF_SUSPECTED, status)
    }

    @Test
    fun `returns LIVE when MN3 passes and CDCN not yet available`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val depth = DepthResult.fromMn3Only(mn3Real = 0.8f, mn3Spoof = 0.2f, cdcnTriggered = true)
        val status = aggregator.classify(texture, depth, null, null, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns SPOOF_SUSPECTED when device detected overlapping face`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.9f, spoofScore = 0.08f, backgroundScore = 0.02f)
        val device = DeviceDetectionResult(
            deviceDetected = true,
            maxConfidence = 0.7f,
            deviceClass = "cell phone",
            overlapWithFace = true,
            spoofScore = 0.84f
        )
        val status = aggregator.classify(texture, null, null, device, null, features)
        assertEquals(PadStatus.SPOOF_SUSPECTED, status)
    }

    @Test
    fun `returns LIVE when device detected but not overlapping face`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.9f, spoofScore = 0.08f, backgroundScore = 0.02f)
        val device = DeviceDetectionResult(
            deviceDetected = true,
            maxConfidence = 0.7f,
            deviceClass = "cell phone",
            overlapWithFace = false,
            spoofScore = 0.35f
        )
        val status = aggregator.classify(texture, null, null, device, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns SPOOF_SUSPECTED when photometric gate fails`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.6f, spoofScore = 0.35f, backgroundScore = 0.05f)
        val photometric = PhotometricResult(
            specularScore = 0.1f, chrominanceScore = 0.1f,
            edgeDofScore = 0.1f, lightingScore = 0.1f, combinedScore = 0.1f
        )
        val status = aggregator.classify(texture, null, null, null, photometric, features)
        assertEquals(PadStatus.SPOOF_SUSPECTED, status)
    }

    @Test
    fun `returns LIVE when photometric passes`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.6f, spoofScore = 0.35f, backgroundScore = 0.05f)
        val photometric = PhotometricResult(
            specularScore = 0.5f, chrominanceScore = 0.5f,
            edgeDofScore = 0.5f, lightingScore = 0.5f, combinedScore = 0.5f
        )
        val status = aggregator.classify(texture, null, null, null, photometric, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns SPOOF_SUSPECTED when frequency gate fires`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val frequency = FrequencyResult(
            moireScore = 0.8f, peakFrequency = 32f, spectralFlatness = 0.3f,
            lbpScreenScore = 0.9f, lbpUniformity = 0.3f, lbpChannelCorrelation = 0.8f
        )
        val status = aggregator.classify(texture, null, frequency, null, null, features)
        assertEquals(PadStatus.SPOOF_SUSPECTED, status)
    }

    @Test
    fun `returns LIVE when only moire is high but LBP is low`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val frequency = FrequencyResult(
            moireScore = 0.8f, peakFrequency = 32f, spectralFlatness = 0.3f,
            lbpScreenScore = 0.3f, lbpUniformity = 0.6f, lbpChannelCorrelation = 0.4f
        )
        val status = aggregator.classify(texture, null, frequency, null, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns LIVE with good texture (no depth data)`() {
        val features = createFeatures(framesCollected = 15)
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val status = aggregator.classify(texture, null, null, null, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `returns LIVE when no texture and no depth data`() {
        val features = createFeatures(framesCollected = 15)
        val status = aggregator.classify(null, null, null, null, null, features)
        assertEquals(PadStatus.LIVE, status)
    }

    @Test
    fun `aggregate score combines all ML layers`() {
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val depth = DepthResult.fromBoth(
            mn3Real = 0.8f, mn3Spoof = 0.2f,
            cdcnScore = 0.8f, cdcnVariance = 0.05f, cdcnMean = 0.8f
        )
        val features = createFeatures(framesCollected = 15)

        val score = aggregator.computeAggregateScore(texture, depth, null)
        assert(score > 0.3f) { "Score $score should be > 0.3" }
        assert(score < 1f) { "Score $score should be < 1" }
    }

    @Test
    fun `aggregate score penalizes device detection`() {
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val features = createFeatures(framesCollected = 15)

        val scoreNoDevice = aggregator.computeAggregateScore(texture, null, null)

        val device = DeviceDetectionResult(
            deviceDetected = true, maxConfidence = 0.8f, deviceClass = "cell phone",
            overlapWithFace = true, spoofScore = 0.96f
        )
        val scoreWithDevice = aggregator.computeAggregateScore(texture, null, device)

        assert(scoreWithDevice < scoreNoDevice) {
            "Score with device ($scoreWithDevice) should be lower than without ($scoreNoDevice)"
        }
    }

    @Test
    fun `aggregate score uses CDCN when available over MN3`() {
        val texture = TextureResult(genuineScore = 0.8f, spoofScore = 0.15f, backgroundScore = 0.05f)
        val features = createFeatures(framesCollected = 15)

        val goodDepth = DepthResult.fromBoth(
            mn3Real = 0.8f, mn3Spoof = 0.2f,
            cdcnScore = 0.9f, cdcnVariance = 0.05f, cdcnMean = 0.9f
        )
        val scoreGoodDepth = aggregator.computeAggregateScore(texture, goodDepth, null)

        val badDepth = DepthResult.fromBoth(
            mn3Real = 0.8f, mn3Spoof = 0.2f,
            cdcnScore = 0.1f, cdcnVariance = 0.01f, cdcnMean = 0.1f
        )
        val scoreBadDepth = aggregator.computeAggregateScore(texture, badDepth, null)

        assert(scoreGoodDepth > scoreBadDepth) {
            "Good depth ($scoreGoodDepth) should score higher than bad depth ($scoreBadDepth)"
        }
    }

    private fun createFeatures(
        faceDetected: Boolean = true,
        faceConfidence: Float = 0.9f,
        framesCollected: Int = 20,
        headMovement: Float = 0f,
        blinkDetected: Boolean = false,
        consecutiveFaceFrames: Int = 10
    ) = TemporalFeatures(
        faceDetected = faceDetected,
        faceConfidence = faceConfidence,
        faceBboxCenterX = 0.5f,
        faceBboxCenterY = 0.5f,
        faceBboxArea = 0.1f,
        headMovementVariance = headMovement,
        faceSizeStability = 0.01f,
        blinkDetected = blinkDetected,
        framesCollected = framesCollected,
        frameSimilarity = 0.95f,
        consecutiveFaceFrames = consecutiveFaceFrames,
        movementSmoothness = 0.9f
    )
}
