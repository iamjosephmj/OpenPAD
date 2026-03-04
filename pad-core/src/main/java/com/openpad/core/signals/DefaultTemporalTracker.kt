package com.openpad.core.signals

import com.openpad.core.InternalPadConfig
import com.openpad.core.detection.FaceDetection
import kotlin.math.sqrt

/**
 * Layer 4: Tracks temporal signals across frames in a sliding window.
 *
 * Ported from DefaultPadFeatureExtractor — computes:
 * - Head movement variance (micro-movement detection)
 * - Face size stability
 * - Blink detection (confidence dip-recovery)
 * - Movement smoothness (trajectory acceleration)
 * - Consecutive face frame count
 */
class DefaultTemporalTracker(
    private val config: InternalPadConfig = InternalPadConfig.Default
) : TemporalSignalTracker {

    private val centerXHistory = ArrayDeque<Float>()
    private val centerYHistory = ArrayDeque<Float>()
    private val areaHistory = ArrayDeque<Float>()
    private val confidenceHistory = ArrayDeque<Float>()
    private var framesCollected = 0
    private var consecutiveFaceFrames = 0
    private var blinkEverDetected = false

    private val windowSize get() = config.slidingWindowSize

    override fun update(detection: FaceDetection?, frameSimilarity: Float): TemporalFeatures {
        framesCollected++

        if (detection == null) {
            consecutiveFaceFrames = 0
            return TemporalFeatures(
                faceDetected = false,
                faceConfidence = 0f,
                faceBboxCenterX = 0f,
                faceBboxCenterY = 0f,
                faceBboxArea = 0f,
                headMovementVariance = 0f,
                faceSizeStability = 0f,
                blinkDetected = false,
                framesCollected = framesCollected,
                frameSimilarity = frameSimilarity,
                consecutiveFaceFrames = 0,
                movementSmoothness = 0f
            )
        }

        consecutiveFaceFrames++

        addToWindow(centerXHistory, detection.centerX)
        addToWindow(centerYHistory, detection.centerY)
        addToWindow(areaHistory, detection.area)
        addToWindow(confidenceHistory, detection.confidence)

        val headMovement = computeMovementVariance()
        val sizeStability = computeStdDev(areaHistory)
        val blink = detectBlink()
        val smoothness = computeMovementSmoothness()

        return TemporalFeatures(
            faceDetected = true,
            faceConfidence = detection.confidence,
            faceBboxCenterX = detection.centerX,
            faceBboxCenterY = detection.centerY,
            faceBboxArea = detection.area,
            headMovementVariance = headMovement,
            faceSizeStability = sizeStability,
            blinkDetected = blink,
            framesCollected = framesCollected,
            frameSimilarity = frameSimilarity,
            consecutiveFaceFrames = consecutiveFaceFrames,
            movementSmoothness = smoothness
        )
    }

    override fun reset() {
        centerXHistory.clear()
        centerYHistory.clear()
        areaHistory.clear()
        confidenceHistory.clear()
        framesCollected = 0
        consecutiveFaceFrames = 0
        blinkEverDetected = false
    }

    private fun addToWindow(deque: ArrayDeque<Float>, value: Float) {
        deque.addLast(value)
        if (deque.size > windowSize) deque.removeFirst()
    }

    /**
     * Combined variance of X and Y center positions.
     * Live faces show involuntary micro-movements; photos are static.
     */
    private fun computeMovementVariance(): Float {
        if (centerXHistory.size < 2) return 0f
        val varX = computeVariance(centerXHistory)
        val varY = computeVariance(centerYHistory)
        return (varX + varY) * 10000f
    }

    /**
     * Detect blink events using face-detection confidence dips.
     * Once detected in a session, stays latched true.
     */
    fun detectBlink(): Boolean {
        if (blinkEverDetected) return true

        // Strategy 1: Confidence dip-recovery
        if (confidenceHistory.size >= BLINK_WINDOW) {
            val recent = confidenceHistory.toList().takeLast(BLINK_WINDOW)
            val baseline = recent.take(3).average().toFloat()
            if (baseline > BLINK_MIN_BASELINE_CONFIDENCE) {
                var foundDip = false
                for (i in 3 until recent.size) {
                    val conf = recent[i]
                    val drop = baseline - conf
                    if (!foundDip && drop >= BLINK_CONFIDENCE_DIP) {
                        foundDip = true
                    } else if (foundDip && drop < BLINK_CONFIDENCE_DIP * 0.3f) {
                        blinkEverDetected = true
                        return true
                    }
                }
            }
        }

        // Strategy 2: Face area dip-recovery
        if (areaHistory.size >= BLINK_WINDOW) {
            val recent = areaHistory.toList().takeLast(BLINK_WINDOW)
            val mean = recent.average().toFloat()
            if (mean > 0f) {
                var foundDip = false
                for (area in recent) {
                    if (!foundDip && area < mean * AREA_DIP_THRESHOLD) {
                        foundDip = true
                    } else if (foundDip && area > mean * AREA_RECOVERY_THRESHOLD) {
                        blinkEverDetected = true
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Movement plausibility: average frame-to-frame acceleration.
     * Real heads move smoothly; video edits / holders produce spikes.
     */
    fun computeMovementSmoothness(): Float {
        if (centerXHistory.size < 3) return 1f

        val xList = centerXHistory.toList()
        val yList = centerYHistory.toList()
        val n = xList.size

        var totalAccel = 0f
        var count = 0
        for (i in 2 until n) {
            val accelX = xList[i] - 2 * xList[i - 1] + xList[i - 2]
            val accelY = yList[i] - 2 * yList[i - 1] + yList[i - 2]
            totalAccel += sqrt(accelX * accelX + accelY * accelY)
            count++
        }

        if (count == 0) return 1f
        val avgAccel = totalAccel / count
        return 1f / (1f + avgAccel * SMOOTHNESS_SCALE)
    }

    internal fun computeVariance(values: ArrayDeque<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun computeStdDev(values: ArrayDeque<Float>): Float {
        return sqrt(computeVariance(values))
    }

    companion object {
        private const val BLINK_WINDOW = 12
        private const val BLINK_MIN_BASELINE_CONFIDENCE = 0.80f
        private const val BLINK_CONFIDENCE_DIP = 0.025f
        private const val AREA_DIP_THRESHOLD = 0.95f
        private const val AREA_RECOVERY_THRESHOLD = 0.98f
        private const val SMOOTHNESS_SCALE = 5000f
    }
}
