package com.openpad.core.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.openpad.core.PadConfig
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.depth.DepthAnalyzer
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.detection.FaceDetector
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.device.DeviceDetector
import com.openpad.core.enhance.FrameEnhancer
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.ndk.NativeChallengeManager
import com.openpad.core.ndk.OpenPadNative
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureAnalyzer
import com.openpad.core.texture.TextureResult
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
    private val config: PadConfig = PadConfig.Default,
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

            // Apply frame enhancement if face is blurry during CHALLENGE_CLOSER
            val analysisBitmap = if (detection != null && shouldEnhance(bitmap, detection.bbox)) {
                val enhanced = frameEnhancer.enhance(bitmap, detection.bbox)
                if (enhanced != null) {
                    enhancementApplied = true
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
                faceCropBitmap = cropFace(analysisBitmap, bbox)
                faceDisplayBitmap = cropFaceForDisplay(bitmap, bbox)
            }

            // Use original bitmap for frame similarity to keep temporal tracking consistent
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

            val input = OpenPadNative.buildInput(
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

            val outputBytes = OpenPadNative.nativeAnalyzeFrame(input)
            val nativeOutput = OpenPadNative.parseOutput(outputBytes)

            nativeChallengeManager.updateFromResult(
                PadResult(
                    status = padStatusFromInt(nativeOutput.padStatus),
                    faceDetection = detection,
                    rawFaceDetection = rawDetection,
                    textureResult = textureResult,
                    depthResult = depthResult,
                    frequencyResult = FrequencyResult(
                        moireScore = nativeOutput.moireScore,
                        peakFrequency = nativeOutput.peakFrequency,
                        spectralFlatness = nativeOutput.spectralFlatness,
                        lbpScreenScore = nativeOutput.lbpScreenScore,
                        lbpUniformity = nativeOutput.lbpUniformity,
                        lbpChannelCorrelation = nativeOutput.lbpChannelCorrelation
                    ),
                    deviceDetectionResult = deviceResult,
                    photometricResult = PhotometricResult(
                        specularScore = nativeOutput.photometricSpecular,
                        chrominanceScore = nativeOutput.photometricChrominance,
                        edgeDofScore = nativeOutput.photometricEdgeDof,
                        lightingScore = nativeOutput.photometricLighting,
                        combinedScore = nativeOutput.photometricCombined
                    ),
                    faceCropBitmap = faceCropBitmap,
                    faceDisplayBitmap = faceDisplayBitmap,
                    temporalFeatures = TemporalFeatures(
                        faceDetected = nativeOutput.temporalFaceDetected,
                        faceConfidence = nativeOutput.temporalFaceConfidence,
                        faceBboxCenterX = nativeOutput.temporalCenterX,
                        faceBboxCenterY = nativeOutput.temporalCenterY,
                        faceBboxArea = nativeOutput.temporalArea,
                        headMovementVariance = nativeOutput.temporalHeadMovement,
                        faceSizeStability = nativeOutput.temporalSizeStability,
                        blinkDetected = nativeOutput.temporalBlinkDetected,
                        framesCollected = nativeOutput.temporalFramesCollected,
                        frameSimilarity = nativeOutput.temporalFrameSimilarity,
                        consecutiveFaceFrames = nativeOutput.temporalConsecutiveFaceFrames,
                        movementSmoothness = nativeOutput.temporalMovementSmoothness
                    ),
                    aggregatedScore = nativeOutput.aggregatedScore,
                    frameSimilarity = nativeOutput.frameSimilarity,
                    faceSharpness = nativeOutput.faceSharpness,
                    enhancementApplied = enhancementApplied,
                    timestampMs = now
                ),
                nativeOutput
            )

            val result = PadResult(
                status = padStatusFromInt(nativeOutput.padStatus),
                faceDetection = detection,
                rawFaceDetection = rawDetection,
                textureResult = textureResult,
                depthResult = depthResult,
                frequencyResult = FrequencyResult(
                    moireScore = nativeOutput.moireScore,
                    peakFrequency = nativeOutput.peakFrequency,
                    spectralFlatness = nativeOutput.spectralFlatness,
                    lbpScreenScore = nativeOutput.lbpScreenScore,
                    lbpUniformity = nativeOutput.lbpUniformity,
                    lbpChannelCorrelation = nativeOutput.lbpChannelCorrelation
                ),
                deviceDetectionResult = deviceResult,
                photometricResult = PhotometricResult(
                    specularScore = nativeOutput.photometricSpecular,
                    chrominanceScore = nativeOutput.photometricChrominance,
                    edgeDofScore = nativeOutput.photometricEdgeDof,
                    lightingScore = nativeOutput.photometricLighting,
                    combinedScore = nativeOutput.photometricCombined
                ),
                faceCropBitmap = faceCropBitmap,
                faceDisplayBitmap = faceDisplayBitmap,
                temporalFeatures = TemporalFeatures(
                    faceDetected = nativeOutput.temporalFaceDetected,
                    faceConfidence = nativeOutput.temporalFaceConfidence,
                    faceBboxCenterX = nativeOutput.temporalCenterX,
                    faceBboxCenterY = nativeOutput.temporalCenterY,
                    faceBboxArea = nativeOutput.temporalArea,
                    headMovementVariance = nativeOutput.temporalHeadMovement,
                    faceSizeStability = nativeOutput.temporalSizeStability,
                    blinkDetected = nativeOutput.temporalBlinkDetected,
                    framesCollected = nativeOutput.temporalFramesCollected,
                    frameSimilarity = nativeOutput.temporalFrameSimilarity,
                    consecutiveFaceFrames = nativeOutput.temporalConsecutiveFaceFrames,
                    movementSmoothness = nativeOutput.temporalMovementSmoothness
                ),
                aggregatedScore = nativeOutput.aggregatedScore,
                frameSimilarity = nativeOutput.frameSimilarity,
                faceSharpness = nativeOutput.faceSharpness,
                enhancementApplied = enhancementApplied,
                timestampMs = now
            )

            onResult(result)
        } finally {
            image.close()
        }
    }

    private fun padStatusFromInt(v: Int): PadStatus = when (v) {
        0 -> PadStatus.ANALYZING
        1 -> PadStatus.NO_FACE
        2 -> PadStatus.LIVE
        3 -> PadStatus.SPOOF_SUSPECTED
        4 -> PadStatus.COMPLETED
        else -> PadStatus.ANALYZING
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

    private fun cropFace(bitmap: Bitmap, bbox: FaceDetection.BBox): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = (bbox.left + bbox.right) / 2f * imgW
        val faceCY = (bbox.top + bbox.bottom) / 2f * imgH
        val faceW = bbox.width() * imgW * CROP_MARGIN
        val faceH = bbox.height() * imgH * CROP_MARGIN
        val side = maxOf(faceW, faceH)

        val left = (faceCX - side / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - side / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + side / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + side / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, FACE_CROP_SIZE, FACE_CROP_SIZE, true)
    }

    /**
     * Crop a fixed-pixel region around the face center from the raw camera frame.
     * Unlike [cropFace], this does NOT normalize the face to fill the output,
     * so a closer face appears proportionally larger — preserving the distance difference
     * between checkpoint captures.
     */
    private fun cropFaceForDisplay(bitmap: Bitmap, bbox: FaceDetection.BBox): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = ((bbox.left + bbox.right) / 2f * imgW).toInt()
        val faceCY = ((bbox.top + bbox.bottom) / 2f * imgH).toInt()
        val half = DISPLAY_CROP_SIZE / 2

        val left = (faceCX - half).coerceIn(0, imgW - 1)
        val top = (faceCY - half).coerceIn(0, imgH - 1)
        val right = (faceCX + half).coerceIn(left + 1, imgW)
        val bottom = (faceCY + half).coerceIn(top + 1, imgH)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    companion object {
        private const val FACE_CROP_SIZE = 112
        private const val DISPLAY_CROP_SIZE = 224
        private const val CROP_MARGIN = 1.3f
        private const val GUIDE_OVAL_WIDTH_FRACTION = 0.65f
        private const val GUIDE_OVAL_HEIGHT_FRACTION = 0.52f
        /** Max face pixel area for enhancement. Above this, the face is large enough already. */
        private const val MAX_FACE_PIXELS_FOR_ENHANCEMENT = 160f * 160f
    }
}
