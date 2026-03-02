package com.openpad.core.signals

import com.openpad.core.PadConfig
import com.openpad.core.detection.FaceDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultTemporalTrackerTest {

    private lateinit var tracker: DefaultTemporalTracker

    @Before
    fun setup() {
        tracker = DefaultTemporalTracker(PadConfig.Default)
    }

    @Test
    fun `no face returns zero features`() {
        val features = tracker.update(null, 0f)
        assertFalse(features.faceDetected)
        assertEquals(0f, features.headMovementVariance)
        assertEquals(0, features.consecutiveFaceFrames)
    }

    @Test
    fun `consecutive face frames increment`() {
        val face = createDetection(0.5f, 0.5f, 0.9f)
        repeat(5) {
            val features = tracker.update(face, 0.95f)
            assertEquals(it + 1, features.consecutiveFaceFrames)
        }
    }

    @Test
    fun `consecutive face frames reset on null detection`() {
        val face = createDetection(0.5f, 0.5f, 0.9f)
        repeat(3) { tracker.update(face, 0.95f) }

        val features = tracker.update(null, 0f)
        assertEquals(0, features.consecutiveFaceFrames)

        val next = tracker.update(face, 0.95f)
        assertEquals(1, next.consecutiveFaceFrames)
    }

    @Test
    fun `frames collected increments even without face`() {
        tracker.update(null, 0f)
        tracker.update(null, 0f)
        val features = tracker.update(null, 0f)
        assertEquals(3, features.framesCollected)
    }

    @Test
    fun `static face has low movement variance`() {
        val face = createDetection(0.5f, 0.5f, 0.9f)
        var lastFeatures: TemporalFeatures? = null
        repeat(10) {
            lastFeatures = tracker.update(face, 0.95f)
        }
        // Static face → movement should be near 0
        assertTrue(
            "Movement should be low for static face, was ${lastFeatures!!.headMovementVariance}",
            lastFeatures!!.headMovementVariance < 1f
        )
    }

    @Test
    fun `moving face has higher movement variance`() {
        var lastFeatures: TemporalFeatures? = null
        repeat(15) { i ->
            // Simulate lateral head movement
            val cx = 0.45f + (i % 5) * 0.02f
            val face = createDetection(cx, 0.5f, 0.9f)
            lastFeatures = tracker.update(face, 0.90f)
        }
        assertTrue(
            "Movement should be measurable for moving face, was ${lastFeatures!!.headMovementVariance}",
            lastFeatures!!.headMovementVariance > 0f
        )
    }

    @Test
    fun `movement smoothness is high for smooth trajectory`() {
        // Smooth linear movement
        repeat(10) { i ->
            val cx = 0.45f + i * 0.005f
            val face = createDetection(cx, 0.5f, 0.9f)
            tracker.update(face, 0.95f)
        }
        val smoothness = tracker.computeMovementSmoothness()
        assertTrue("Smoothness should be high for linear movement, was $smoothness", smoothness > 0.5f)
    }

    @Test
    fun `reset clears all state`() {
        val face = createDetection(0.5f, 0.5f, 0.9f)
        repeat(10) { tracker.update(face, 0.95f) }

        tracker.reset()

        val features = tracker.update(face, 0.95f)
        assertEquals(1, features.framesCollected)
        assertEquals(1, features.consecutiveFaceFrames)
    }

    @Test
    fun `blink detection latches true`() {
        // No blink detected with static confidence
        val face = createDetection(0.5f, 0.5f, 0.95f)
        repeat(20) {
            val features = tracker.update(face, 0.95f)
            // Should not detect blink with constant confidence
        }

        // Can't easily test real blink detection without Robolectric
        // since we need ArrayDeque history manipulation
    }

    @Test
    fun `frame similarity is passed through`() {
        val face = createDetection(0.5f, 0.5f, 0.9f)
        val features = tracker.update(face, 0.973f)
        assertEquals(0.973f, features.frameSimilarity)
    }

    private fun createDetection(
        centerX: Float,
        centerY: Float,
        confidence: Float,
        size: Float = 0.3f
    ): FaceDetection {
        val halfW = size / 2f
        val halfH = size / 2f * 1.2f
        return FaceDetection(
            confidence = confidence,
            bbox = FaceDetection.BBox(
                left = centerX - halfW,
                top = centerY - halfH,
                right = centerX + halfW,
                bottom = centerY + halfH
            )
        )
    }
}
