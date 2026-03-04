package com.openpad.core.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.openpad.core.InternalPadConfig
import com.openpad.core.PadResult
import com.openpad.core.depth.DepthAnalyzer
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.detection.FaceDetector
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.device.DeviceDetector
import com.openpad.core.texture.TextureResult
import com.openpad.core.enhance.FrameEnhancer
import com.openpad.core.ndk.NativeChallengeManager
import com.openpad.core.ndk.OpenPadNative
import com.openpad.core.texture.TextureAnalyzer
import android.graphics.Bitmap

/**
 * CameraX [ImageAnalysis.Analyzer] that orchestrates the full PAD pipeline per frame.
 *
 * TFLite (face, texture, depth, device) runs in Kotlin. FFT, LBP, photometric,
 * temporal, aggregation, stabilizer, and challenge run in the native C layer.
 */
class PadFrameAnalyzer internal constructor(
    private val faceDetector: FaceDetector,
    private val textureAnalyzer: TextureAnalyzer,
    private val depthAnalyzer: DepthAnalyzer,
    private val deviceDetector: DeviceDetector,
    private val frameEnhancer: FrameEnhancer,
    private val nativeChallengeManager: NativeChallengeManager,
    private val config: InternalPadConfig = InternalPadConfig.Default,
    private val onResult: (PadResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastFrameTimeMs: Long = 0L
    private val minFrameIntervalMs = 1000L / config.maxFps

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameTimeMs < minFrameIntervalMs) return
            lastFrameTimeMs = now

            val bitmap = BitmapConverter.imageToBitmap(image) ?: return
            var analysisBitmapRef: Bitmap? = null
            try {

            val rawDetection = faceDetector.detect(bitmap)
            val detection = rawDetection?.let { det ->
                if (isFaceInGuideOval(det)) det else null
            }

            var textureResult: TextureResult? = null
            var depthResult: DepthResult? = null
            var deviceResult: DeviceDetectionResult? = null
            var faceCropBitmap: Bitmap? = null
            var faceDisplayBitmap: Bitmap? = null
            var enhancementApplied = false

            val analysisBitmap = if (detection != null && shouldEnhance(bitmap, detection.bbox)) {
                val enhanced = frameEnhancer.enhance(bitmap, detection.bbox)
                if (enhanced != null) {
                    enhancementApplied = true
                    analysisBitmapRef = enhanced
                    enhanced
                } else {
                    bitmap
                }
            } else {
                bitmap
            }

            if (detection != null) {
                val bbox = detection.bbox
                depthResult = depthAnalyzer.analyze(analysisBitmap, bbox)
                textureResult = textureAnalyzer.analyze(analysisBitmap, bbox)
                deviceResult = deviceDetector.analyze(analysisBitmap, bbox)
                faceCropBitmap = BitmapConverter.cropFace(analysisBitmap, bbox)
                faceDisplayBitmap = BitmapConverter.cropFaceForDisplay(bitmap, bbox)
            }

            val input = NativeInputBuilder.build(
                bitmap = bitmap,
                analysisBitmap = analysisBitmap,
                detection = detection,
                textureResult = textureResult,
                depthResult = depthResult,
                deviceResult = deviceResult
            )

            val outputBytes = OpenPadNative.nativeAnalyzeFrame(input)
            val nativeOutput = OpenPadNative.parseOutput(outputBytes)

            val result = PadResultMapper.fromNativeOutput(
                nativeOutput = nativeOutput,
                detection = detection,
                rawDetection = rawDetection,
                textureResult = textureResult,
                depthResult = depthResult,
                deviceResult = deviceResult,
                faceCropBitmap = faceCropBitmap,
                faceDisplayBitmap = faceDisplayBitmap,
                enhancementApplied = enhancementApplied,
                timestampMs = now
            )

            nativeChallengeManager.updateFromResult(result, nativeOutput)
            onResult(result)

            } finally {
                analysisBitmapRef?.recycle()
                bitmap.recycle()
            }
        } finally {
            image.close()
        }
    }

    private fun shouldEnhance(bitmap: Bitmap, bbox: FaceDetection.BBox): Boolean {
        if (!config.enableFrameEnhancement) return false
        if (nativeChallengeManager.phase != ChallengePhase.CHALLENGE_CLOSER) return false
        // Skip if face is already large enough — downscaling to 128 then SR is pointless
        val facePixelArea = bbox.width() * bitmap.width * bbox.height() * bitmap.height
        if (facePixelArea > MAX_FACE_PIXELS_FOR_ENHANCEMENT) return false
        // Let ESPCN decide: run the model, its quality gate discards if enhancement didn't help
        return true
    }

    private fun isFaceInGuideOval(detection: FaceDetection): Boolean {
        val cx = detection.centerX
        val cy = detection.centerY
        val rx = GUIDE_OVAL_WIDTH_FRACTION / 2f
        val ry = GUIDE_OVAL_HEIGHT_FRACTION / 2f
        val dx = (cx - 0.5f) / rx
        val dy = (cy - 0.5f) / ry
        return (dx * dx + dy * dy) <= 1.0f
    }

    companion object {
        private const val GUIDE_OVAL_WIDTH_FRACTION = 0.65f
        private const val GUIDE_OVAL_HEIGHT_FRACTION = 0.52f
        /** Max face pixel area for enhancement. Above this, the face is large enough already. */
        private const val MAX_FACE_PIXELS_FOR_ENHANCEMENT = 160f * 160f
    }
}
