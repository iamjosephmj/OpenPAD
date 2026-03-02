package com.openpad.core.detection

import android.graphics.Bitmap

/** Layer 1 interface: detects faces in a frame. */
interface FaceDetector {
    /** Returns the highest-confidence face detection, or null if no face found. */
    fun detect(bitmap: Bitmap): FaceDetection?

    /** Release resources (interpreter, etc). */
    fun close()
}
