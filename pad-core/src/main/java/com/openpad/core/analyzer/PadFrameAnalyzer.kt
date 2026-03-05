package com.openpad.core.analyzer

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.openpad.core.InternalPadConfig
import com.openpad.core.PadResult
import com.openpad.core.depth.DepthAnalyzer
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.detection.FaceDetector
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.device.ScreenReflectionDetector
import com.openpad.core.device.ScreenReflectionResult
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
    private val screenReflectionDetector: ScreenReflectionDetector,
    private val frameEnhancer: FrameEnhancer,
    private val framePreprocessor: FramePreprocessor?,
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
            var preprocessedRef: Bitmap? = null
            try {

            val rawDetection = faceDetector.detect(bitmap)
            val detection = rawDetection?.let { det ->
                if (isFaceInGuideOval(det)) det else null
            }

            var textureResult: TextureResult? = null
            var depthResult: DepthResult? = null
            var deviceResult: DeviceDetectionResult? = null
            var screenReflectionResult: ScreenReflectionResult? = null
            var faceCropBitmap: Bitmap? = null
            var faceDisplayBitmap: Bitmap? = null
            var enhancementApplied = false
            var faceLuminance = 0.5f

            val baseBitmap = if (detection != null && framePreprocessor != null) {
                faceLuminance = BitmapConverter.computeFaceLuminance(bitmap, detection.bbox)
                val pp = framePreprocessor.preprocess(bitmap, faceLuminance)
                preprocessedRef = pp
                pp
            } else {
                bitmap
            }

            val analysisBitmap = if (detection != null && shouldEnhance(baseBitmap, detection.bbox)) {
                val enhanced = frameEnhancer.enhance(baseBitmap, detection.bbox)
                if (enhanced != null) {
                    enhancementApplied = true
                    analysisBitmapRef = enhanced
                    enhanced
                } else {
                    baseBitmap
                }
            } else {
                baseBitmap
            }

            if (detection != null) {
                val bbox = detection.bbox
                depthResult = depthAnalyzer.analyze(analysisBitmap, bbox)
                textureResult = textureAnalyzer.analyze(analysisBitmap, bbox)
                deviceResult = deviceDetector.analyze(analysisBitmap, bbox)
                screenReflectionResult = screenReflectionDetector.analyze(analysisBitmap, bbox)
                faceCropBitmap = BitmapConverter.cropFace(bitmap, bbox)
                faceDisplayBitmap = BitmapConverter.cropFaceForDisplay(bitmap, bbox)
                if (framePreprocessor == null) {
                    faceLuminance = BitmapConverter.computeFaceLuminance(bitmap, bbox)
                }
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
                screenReflectionResult = screenReflectionResult,
                faceCropBitmap = faceCropBitmap,
                faceDisplayBitmap = faceDisplayBitmap,
                enhancementApplied = enhancementApplied,
                faceLuminance = faceLuminance,
                timestampMs = now
            )

            val gatedResult = applyTemporalGates(result)
            nativeChallengeManager.updateFromResult(gatedResult, nativeOutput)
            onResult(gatedResult)

            } finally {
                analysisBitmapRef?.recycle()
                preprocessedRef?.recycle()
                bitmap.recycle()
            }
        } finally {
            image.close()
        }
    }

    /**
     * Downgrades LIVE to SPOOF_SUSPECTED when temporal signals or the
     * screen reflection detector indicate a presentation attack.
     * Only overrides LIVE status; other statuses pass through unchanged.
     */
    private fun applyTemporalGates(result: PadResult): PadResult {
        if (result.status != PadStatus.LIVE) return result
        val tf = result.temporalFeatures ?: return result
        if (tf.framesCollected < config.minFramesForDecision) return result
        if (tf.frameSimilarity >= config.staticFrameThreshold) {
            return result.copy(status = PadStatus.SPOOF_SUSPECTED)
        }
        if (tf.consecutiveFaceFrames >= config.minConsecutiveFaceFrames &&
            tf.headMovementVariance < config.minMotionVariance
        ) {
            return result.copy(status = PadStatus.SPOOF_SUSPECTED)
        }
        val sr = result.screenReflectionResult
        if (sr != null &&
            sr.spoofSignalCount >= config.screenReflectionMinSignals &&
            sr.maxConfidence >= config.screenReflectionConfidenceThreshold
        ) {
            Log.e(TAG, "SCREEN_GATE triggered: signals=${sr.spoofSignalCount} " +
                "maxConf=${"%.3f".format(sr.maxConfidence)} " +
                "threshold=${config.screenReflectionConfidenceThreshold} " +
                "minSignals=${config.screenReflectionMinSignals}")
            return result.copy(status = PadStatus.SPOOF_SUSPECTED)
        }
        return result
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
        private const val TAG = "DA8966"
        private const val GUIDE_OVAL_WIDTH_FRACTION = 0.65f
        private const val GUIDE_OVAL_HEIGHT_FRACTION = 0.52f
        /** Max face pixel area for enhancement. Above this, the face is large enough already. */
        private const val MAX_FACE_PIXELS_FOR_ENHANCEMENT = 160f * 160f
    }
}
