package com.openpad.core

import android.graphics.Bitmap
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.depth.DepthResult
import com.openpad.core.detection.FaceDetection
import com.openpad.core.device.ScreenReflectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult

/**
 * Per-frame output from the PAD pipeline, containing results from all layers.
 */
data class PadResult(
    val status: PadStatus,
    val faceDetection: FaceDetection?,
    /** Raw face detection before oval-guide filtering (null = no face at all). */
    val rawFaceDetection: FaceDetection? = null,
    val textureResult: TextureResult?,
    val depthResult: DepthResult?,
    val frequencyResult: FrequencyResult?,
    val screenReflectionResult: ScreenReflectionResult?,
    val photometricResult: PhotometricResult?,
    /** 112x112 face crop for checkpoint capture (null when no face detected). */
    val faceCropBitmap: Bitmap? = null,
    /** Display-quality face crop that preserves natural face size (null when no face detected). */
    val faceDisplayBitmap: Bitmap? = null,
    val temporalFeatures: TemporalFeatures?,
    val aggregatedScore: Float,
    val frameSimilarity: Float,
    val faceSharpness: Float,
    /** True when ESPCN frame enhancement was applied to this frame. */
    val enhancementApplied: Boolean = false,
    /** Average luminance of the face region [0, 1]. Used for ambient light adaptation. */
    val faceLuminance: Float = 0.5f,
    val timestampMs: Long
)
