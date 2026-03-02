package com.openpad.core.signals

import com.openpad.core.detection.FaceDetection

/** Layer 4 interface: tracks temporal signals across frames. */
interface TemporalSignalTracker {
    /**
     * Process a new frame and return accumulated temporal features.
     * @param detection Face detection for this frame, or null if no face.
     * @param frameSimilarity Similarity to previous frame [0..1].
     */
    fun update(detection: FaceDetection?, frameSimilarity: Float): TemporalFeatures

    fun reset()
}
