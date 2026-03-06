package com.openpad.core.benchmark

import android.graphics.Bitmap
import com.openpad.core.InternalPadConfig
import com.openpad.core.PadPipeline
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.analyzer.BitmapConverter
import com.openpad.core.ndk.OpenPadNative

/**
 * Processes Bitmap frames through the full PAD pipeline for benchmarking.
 *
 * Uses the native C layer (NDK) for FFT, LBP, photometric, temporal, aggregation, and challenge.
 * TFLite (face, texture, depth) runs in Kotlin.
 */
class BenchmarkFrameProcessor(
    private val pipeline: PadPipeline,
    private val config: InternalPadConfig = InternalPadConfig.Default
) {

    fun processFrame(bitmap: Bitmap): FrameScore {
        val startMs = System.currentTimeMillis()

        val detection = pipeline.faceDetector.detect(bitmap)
        val textureResult = detection?.let { pipeline.textureAnalyzer.analyze(bitmap, it.bbox) }
        val depthResult = detection?.let { pipeline.depthModels.analyze(bitmap, it.bbox) }

        val frameDownsampled = BitmapConverter.downsampleToGray(bitmap)
        val faceCrop64Gray = if (detection != null) {
            BitmapConverter.cropFaceTo64Gray(bitmap, detection.bbox)
        } else FloatArray(64 * 64) { 0f }
        val faceCrop64Argb = if (detection != null) {
            BitmapConverter.cropFaceTo64Argb(bitmap, detection.bbox)
        } else ByteArray(64 * 64 * 4)
        val faceCrop80Argb = if (detection != null) {
            BitmapConverter.cropFaceTo80Argb(bitmap, detection.bbox)
        } else ByteArray(80 * 80 * 4)

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
            deviceDetected = false,
            deviceOverlap = false,
            deviceMaxConf = 0f,
            deviceSpoof = 0f
        )

        val outputBytes = OpenPadNative.nativeAnalyzeFrame(input)
        val out = OpenPadNative.parseOutput(outputBytes)

        val elapsed = System.currentTimeMillis() - startMs

        return FrameScore(
            rawStatus = PadStatus.fromInt(out.padStatus),
            stableStatus = PadStatus.fromInt(out.padStatus),
            aggregatedScore = out.aggregatedScore,
            textureGenuineScore = textureResult?.genuineScore,
            textureSpoofScore = textureResult?.spoofScore,
            depthScore = depthResult?.depthScore,
            mn3RealScore = depthResult?.mn3RealScore,
            mn3SpoofScore = depthResult?.mn3SpoofScore,
            cdcnDepthScore = depthResult?.cdcnDepthScore,
            cdcnTriggered = depthResult?.cdcnTriggered,
            cdcnAvailable = depthResult?.cdcnAvailable,
            moireScore = out.moireScore,
            lbpScreenScore = out.lbpScreenScore,
            spectralFlatness = out.spectralFlatness,
            deviceDetected = false,
            deviceSpoofScore = null,
            photometricCombinedScore = out.photometricCombined,
            specularScore = out.photometricSpecular,
            chrominanceScore = out.photometricChrominance,
            edgeDofScore = out.photometricEdgeDof,
            lightingScore = out.photometricLighting,
            headMovementVariance = out.temporalHeadMovement,
            blinkDetected = out.temporalBlinkDetected,
            movementSmoothness = out.temporalMovementSmoothness,
            frameSimilarity = out.frameSimilarity,
            faceSharpness = out.faceSharpness,
            faceDetected = detection != null,
            faceConfidence = detection?.confidence,
            processingTimeMs = elapsed
        )
    }

    fun resetState() {
        OpenPadNative.nativeReset()
    }
}
