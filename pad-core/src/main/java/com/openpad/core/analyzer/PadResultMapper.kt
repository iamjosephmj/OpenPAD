package com.openpad.core.analyzer

import android.graphics.Bitmap
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.ndk.NativeFrameOutput
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult

/**
 * Maps [NativeFrameOutput] plus Kotlin-side ML results into a [PadResult].
 *
 * Eliminates the duplicated PadResult constructor calls that previously
 * existed in [PadFrameAnalyzer.analyze].
 */
internal object PadResultMapper {

    fun fromNativeOutput(
        nativeOutput: NativeFrameOutput,
        detection: FaceDetection?,
        rawDetection: FaceDetection?,
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        deviceResult: DeviceDetectionResult?,
        faceCropBitmap: Bitmap?,
        faceDisplayBitmap: Bitmap?,
        enhancementApplied: Boolean,
        timestampMs: Long
    ): PadResult = PadResult(
        status = PadStatus.fromInt(nativeOutput.padStatus),
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
        timestampMs = timestampMs
    )
}
