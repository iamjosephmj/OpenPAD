package com.openpad.core.enhance

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Enhances a face region in a camera frame to compensate for defocus blur.
 *
 * Implementations should only modify the face region and return a new bitmap
 * with the enhanced face composited back. Returns `null` if enhancement is
 * not possible (e.g. model not loaded), in which case the pipeline uses the
 * original bitmap.
 */
interface FrameEnhancer {
    fun enhance(bitmap: Bitmap, faceBbox: FaceDetection.BBox): Bitmap?
    fun close()
}
