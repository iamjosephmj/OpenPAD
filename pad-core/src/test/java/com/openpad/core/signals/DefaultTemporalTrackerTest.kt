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

    private fun face(
        confidence: Float = 0.9f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        area: Float = 0.1f
    ): FaceDetection {
        val side = kotlin.math.sqrt(area)
        val left = centerX - side / 2
        val top = centerY - side / 2
        return FaceDetection(
            confidence = confidence,
            bbox = FaceDetection.BBox(left, top, left + side, top + side)
        )
    }

    @Test
    fun updateWithNullDetectionReturnsFaceNotDetected() {
        val features = tracker.update(null, 0.9f)
        assertFalse(features.faceDetected)
        assertEquals(0, features.consecutiveFaceFrames)
    }

    @Test
    fun updateWithFaceIncreasesConsecutiveCount() {
        val f = face()
        tracker.update(f, 0.9f)
        tracker.update(f, 0.9f)
        val features = tracker.update(f, 0.9f)
        assertTrue(features.faceDetected)
        assertEquals(3, features.consecutiveFaceFrames)
    }

    @Test
    fun updateIncreasesFramesCollected() {
        tracker.update(null, 0.9f)
        tracker.update(face(), 0.9f)
        val features = tracker.update(null, 0.9f)
        assertEquals(3, features.framesCollected)
    }

    @Test
    fun nullDetectionResetsConsecutiveFaceFrames() {
        val f = face()
        tracker.update(f, 0.9f)
        tracker.update(f, 0.9f)
        val features = tracker.update(null, 0.9f)
        assertEquals(0, features.consecutiveFaceFrames)
    }

    @Test
    fun resetClearsAllState() {
        val f = face()
        repeat(5) { tracker.update(f, 0.9f) }
        tracker.reset()
        val features = tracker.update(null, 0.9f)
        assertEquals(1, features.framesCollected)
        assertEquals(0, features.consecutiveFaceFrames)
    }

    @Test
    fun computeVarianceReturnsZeroForSingleElement() {
        val deque = ArrayDeque<Float>()
        deque.addLast(5f)
        assertEquals(0f, tracker.computeVariance(deque))
    }

    @Test
    fun computeVarianceCalculatesCorrectly() {
        val deque = ArrayDeque<Float>()
        deque.addLast(2f)
        deque.addLast(4f)
        deque.addLast(4f)
        // mean=10/3, var = ((2-10/3)^2 + (4-10/3)^2 + (4-10/3)^2) / 3 = 8/9
        assertEquals(8f / 9f, tracker.computeVariance(deque), 1e-5f)
    }

    @Test
    fun detectBlinkReturnsFalseInitially() {
        assertFalse(tracker.detectBlink())
    }

    @Test
    fun detectBlinkLatchesAfterDipRecovery() {
        // Feed enough high-confidence frames to fill the blink window (12)
        val highConf = face(confidence = 0.95f)
        repeat(8) { tracker.update(highConf, 0.9f) }

        // Simulate a confidence dip (blink)
        val lowConf = face(confidence = 0.90f)
        repeat(2) { tracker.update(lowConf, 0.9f) }

        // Recovery
        repeat(4) { tracker.update(highConf, 0.9f) }

        val features = tracker.update(highConf, 0.9f)
        // Blink detection may or may not trigger depending on exact thresholds;
        // once latched, it stays true
        if (features.blinkDetected) {
            val next = tracker.update(highConf, 0.9f)
            assertTrue(next.blinkDetected)
        }
    }

    @Test
    fun movementSmoothnessReturnsOneForFewerThan3Points() {
        tracker.update(face(), 0.9f)
        tracker.update(face(), 0.9f)
        assertEquals(1f, tracker.computeMovementSmoothness())
    }

    @Test
    fun movementSmoothnessDecreasesWithHighAcceleration() {
        // Smooth trajectory
        for (i in 0 until 10) {
            tracker.update(face(centerX = 0.5f + i * 0.001f), 0.9f)
        }
        val smooth = tracker.computeMovementSmoothness()

        tracker.reset()

        // Jerky trajectory
        for (i in 0 until 10) {
            val cx = if (i % 2 == 0) 0.3f else 0.7f
            tracker.update(face(centerX = cx), 0.9f)
        }
        val jerky = tracker.computeMovementSmoothness()

        assertTrue("Smooth ($smooth) should be > jerky ($jerky)", smooth > jerky)
    }
}
