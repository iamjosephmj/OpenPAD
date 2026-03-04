package com.openpad.core.analyzer

import android.graphics.Bitmap
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.ndk.OpenPadNative
import com.openpad.core.texture.TextureResult

/**
 * Assembles the byte array input for the native C analysis layer.
 *
 * Handles downsampling, face crop preparation, and marshalling all
 * ML model outputs into the native input format.
 */
internal object NativeInputBuilder {

    fun build(
        bitmap: Bitmap,
        analysisBitmap: Bitmap,
        detection: FaceDetection?,
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        deviceResult: DeviceDetectionResult?
    ): ByteArray {
        val frameDownsampled = BitmapConverter.downsampleToGray(bitmap)

        val faceCrop64Gray = if (detection != null) {
            BitmapConverter.cropFaceTo64Gray(analysisBitmap, detection.bbox)
        } else {
            FloatArray(64 * 64) { 0f }
        }
        val faceCrop64Argb = if (detection != null) {
            BitmapConverter.cropFaceTo64Argb(analysisBitmap, detection.bbox)
        } else {
            ByteArray(64 * 64 * 4)
        }
        val faceCrop80Argb = if (detection != null) {
            BitmapConverter.cropFaceTo80Argb(analysisBitmap, detection.bbox)
        } else {
            ByteArray(80 * 80 * 4)
        }

        return OpenPadNative.buildInput(
            hasFace = detection != null,
            centerX = detection?.centerX ?: 0f,
            centerY = detection?.centerY ?: 0f,
            area = detection?.area ?: 0f,
            confidence = detection?.confidence ?: 0f,
            frameDownsampled = frameDownsampled,
            faceCrop64Gray = faceCrop64Gray,
            faceCrop64Argb = faceCrop64Argb,
            faceCrop80Argb = faceCrop80Argb,
            textureGenuine = textureResult?.genuineScore ?: 0.5f,
            mn3Real = depthResult?.mn3RealScore,
            cdcn = depthResult?.cdcnDepthScore,
            deviceDetected = deviceResult?.deviceDetected ?: false,
            deviceOverlap = deviceResult?.overlapWithFace ?: false,
            deviceMaxConf = deviceResult?.maxConfidence ?: 0f,
            deviceSpoof = deviceResult?.spoofScore ?: 0f
        )
    }
}
