package com.openpad.core.ndk

import com.openpad.core.PadConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JNI bridge to the native OpenPad C layer (NDK).
 *
 * The native layer implements: FFT moiré, LBP screen, photometric, temporal tracking,
 * weighted aggregation, state stabilizer, and challenge state machine.
 */
object OpenPadNative {

    init {
        System.loadLibrary("openpad")
    }

    /**
     * Initialize the pipeline with config. Must be called before [nativeAnalyzeFrame].
     */
    external fun nativeInit(configBytes: ByteArray)

    /**
     * Reset pipeline state (e.g. when starting a new session).
     */
    external fun nativeReset()

    /** Challenge: advance to LIVE (user passed). */
    external fun nativeChallengeAdvanceToLive()

    /** Challenge: handle spoof. Returns true if max attempts reached. */
    external fun nativeChallengeHandleSpoof(): Boolean

    /** Challenge: advance to DONE. */
    external fun nativeChallengeAdvanceToDone()

    /**
     * Analyze one frame. Returns output bytes (128 bytes, little-endian).
     * Input must be 62510 bytes. Use [buildInput] to construct.
     */
    external fun nativeAnalyzeFrame(inputBytes: ByteArray): ByteArray

    /**
     * Serialize PadConfig to the byte format expected by nativeInit.
     * Layout: 21 f32 (bytes 0-83), padding (bytes 84-127), 11 i32 (bytes 128-171).
     */
    fun configToBytes(config: PadConfig): ByteArray {
        val buf = ByteBuffer.allocate(172).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(config.minFaceConfidence)          // 0
        buf.putFloat(config.textureGenuineThreshold)    // 4
        buf.putFloat(config.positioningMinFaceArea)     // 8
        buf.putFloat(config.positioningMaxFaceArea)     // 12
        buf.putFloat(config.positioningCenterTolerance) // 16
        buf.putFloat(config.challengeCloserMinIncrease) // 20
        buf.putFloat(config.challengeCenterTolerance)   // 24
        buf.putFloat(config.mn3GateThreshold)           // 28
        buf.putFloat(config.depthFlatnessThreshold)     // 32
        buf.putFloat(config.deviceConfidenceThreshold)  // 36
        buf.putFloat(config.moireThreshold)             // 40
        buf.putFloat(config.lbpScreenThreshold)         // 44
        buf.putFloat(config.photometricMinScore)        // 48
        buf.putFloat(config.textureWeight)              // 52
        buf.putFloat(config.mn3Weight)                  // 56
        buf.putFloat(config.cdcnWeight)                 // 60
        buf.putFloat(config.deviceWeight)               // 64
        buf.putFloat(config.genuineProbabilityThreshold)// 68
        buf.putFloat(config.spoofAttemptPenaltyPerCount)// 72
        buf.putFloat(config.maxGenuineProbabilityThreshold) // 76
        buf.putFloat(config.faceConsistencyThreshold)   // 80
        buf.position(128) // skip padding to int32 section
        buf.putInt(config.minFramesForDecision)         // 128
        buf.putInt(config.slidingWindowSize)            // 132
        buf.putInt(config.minConsecutiveFaceFrames)     // 136
        buf.putInt(config.enterConsecutive)              // 140
        buf.putInt(config.exitConsecutive)               // 144
        buf.putInt(config.positioningStableFrames)      // 148
        buf.putInt(config.challengeStableFrames)        // 152
        buf.putInt(config.challengeMinFrames)           // 156
        buf.putInt(config.analyzingStableFrames)        // 160
        buf.putInt(config.maxSpoofAttempts)             // 164
        buf.putInt(config.maxFps)                       // 168
        return buf.array()
    }

    /**
     * Build input buffer for nativeAnalyzeFrame.
     * Layout matches native INPUT_SIZE (62510 bytes).
     */
    fun buildInput(
        hasFace: Boolean,
        centerX: Float,
        centerY: Float,
        area: Float,
        confidence: Float,
        frameDownsampled: FloatArray,
        faceCrop64Gray: FloatArray,
        faceCrop64Argb: ByteArray,
        faceCrop80Argb: ByteArray,
        textureGenuine: Float,
        mn3Real: Float?,
        cdcn: Float?,
        deviceDetected: Boolean,
        deviceOverlap: Boolean,
        deviceMaxConf: Float,
        deviceSpoof: Float
    ): ByteArray {
        val buf = ByteBuffer.allocate(62510).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(if (hasFace) 1 else 0)
        buf.put(0) // pad
        buf.putFloat(centerX)
        buf.putFloat(centerY)
        buf.putFloat(area)
        buf.putFloat(confidence)
        for (f in frameDownsampled) buf.putFloat(f)
        for (f in faceCrop64Gray) buf.putFloat(f)
        buf.put(faceCrop64Argb)
        buf.put(faceCrop80Argb)
        buf.putFloat(textureGenuine)
        buf.putFloat(mn3Real ?: 0f)
        buf.put(if (cdcn != null) 1 else 0)
        buf.putFloat(cdcn ?: 0f)
        buf.put(if (deviceDetected) 1 else 0)
        buf.put(if (deviceOverlap) 1 else 0)
        buf.putFloat(deviceMaxConf)
        buf.putFloat(deviceSpoof)
        return buf.array()
    }

    /**
     * Parse output from nativeAnalyzeFrame.
     */
    fun parseOutput(bytes: ByteArray): NativeFrameOutput {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return NativeFrameOutput(
            padStatus = buf.getInt(0),
            aggregatedScore = buf.getFloat(4),
            frameSimilarity = buf.getFloat(8),
            faceSharpness = buf.getFloat(12),
            challengePhase = buf.getInt(16),
            captureCheckpoint1 = bytes.getOrNull(20) == 1.toByte(),
            captureCheckpoint2 = bytes.getOrNull(21) == 1.toByte(),
            challengeBaselineArea = buf.getFloat(112),
            challengeTotalFrames = buf.getInt(116),
            challengeHoldFrames = buf.getInt(120),
            challengeMaxAreaIncrease = buf.getFloat(124),
            moireScore = buf.getFloat(22),
            peakFrequency = buf.getFloat(26),
            spectralFlatness = buf.getFloat(30),
            lbpScreenScore = buf.getFloat(34),
            lbpUniformity = buf.getFloat(38),
            lbpEntropy = buf.getFloat(42),
            lbpChannelCorrelation = buf.getFloat(46),
            photometricSpecular = buf.getFloat(50),
            photometricChrominance = buf.getFloat(54),
            photometricEdgeDof = buf.getFloat(58),
            photometricLighting = buf.getFloat(62),
            photometricCombined = buf.getFloat(66),
            temporalFaceDetected = bytes.getOrNull(70) == 1.toByte(),
            temporalFaceConfidence = buf.getFloat(71),
            temporalCenterX = buf.getFloat(75),
            temporalCenterY = buf.getFloat(79),
            temporalArea = buf.getFloat(83),
            temporalHeadMovement = buf.getFloat(87),
            temporalSizeStability = buf.getFloat(91),
            temporalBlinkDetected = bytes.getOrNull(95) == 1.toByte(),
            temporalFramesCollected = buf.getInt(96),
            temporalFrameSimilarity = buf.getFloat(100),
            temporalConsecutiveFaceFrames = buf.getInt(104),
            temporalMovementSmoothness = buf.getFloat(108)
        )
    }
}

data class NativeFrameOutput(
    val padStatus: Int,
    val aggregatedScore: Float,
    val frameSimilarity: Float,
    val faceSharpness: Float,
    val challengePhase: Int,
    val captureCheckpoint1: Boolean,
    val captureCheckpoint2: Boolean,
    val challengeBaselineArea: Float,
    val challengeTotalFrames: Int,
    val challengeHoldFrames: Int,
    val challengeMaxAreaIncrease: Float,
    val moireScore: Float,
    val peakFrequency: Float,
    val spectralFlatness: Float,
    val lbpScreenScore: Float,
    val lbpUniformity: Float,
    val lbpEntropy: Float,
    val lbpChannelCorrelation: Float,
    val photometricSpecular: Float,
    val photometricChrominance: Float,
    val photometricEdgeDof: Float,
    val photometricLighting: Float,
    val photometricCombined: Float,
    val temporalFaceDetected: Boolean,
    val temporalFaceConfidence: Float,
    val temporalCenterX: Float,
    val temporalCenterY: Float,
    val temporalArea: Float,
    val temporalHeadMovement: Float,
    val temporalSizeStability: Float,
    val temporalBlinkDetected: Boolean,
    val temporalFramesCollected: Int,
    val temporalFrameSimilarity: Float,
    val temporalConsecutiveFaceFrames: Int,
    val temporalMovementSmoothness: Float
)
