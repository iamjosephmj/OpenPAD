package com.openpad.core

import android.content.Context
import com.openpad.core.analyzer.PadFrameAnalyzer
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.depth.CascadedDepthAnalyzer
import com.openpad.core.depth.CdcnDepthAnalyzer
import com.openpad.core.depth.DepthAnalyzer
import com.openpad.core.detection.FaceDetector
import com.openpad.core.detection.MediaPipeFaceDetector
import com.openpad.core.device.DeviceDetector
import com.openpad.core.device.SsdDeviceDetector
import com.openpad.core.embedding.FaceEmbeddingAnalyzer
import com.openpad.core.embedding.MobileFaceNetAnalyzer
import com.openpad.core.ndk.NativeChallengeManager
import com.openpad.core.ndk.OpenPadNative
import com.openpad.core.texture.MiniFasNetAnalyzer
import com.openpad.core.texture.TextureAnalyzer
import timber.log.Timber

/**
 * Entry point for the Open-PAD pipeline.
 *
 * Creates all components with manual construction (no DI framework).
 * FFT, LBP, photometric, temporal, aggregation, stabilizer, and challenge logic
 * run in the native C layer (NDK). TFLite models (face, texture, depth, device,
 * embedding) remain in Kotlin.
 */
class PadPipeline private constructor(
    val faceDetector: FaceDetector,
    val textureAnalyzer: TextureAnalyzer,
    val depthAnalyzer: DepthAnalyzer,
    val depthModels: CdcnDepthAnalyzer,
    val deviceDetector: DeviceDetector,
    val embeddingAnalyzer: FaceEmbeddingAnalyzer,
    internal val nativeChallengeManager: NativeChallengeManager,
    val config: PadConfig
) {
    /** Create a CameraX ImageAnalysis.Analyzer wired to the full pipeline. */
    fun createFrameAnalyzer(onResult: (PadResult) -> Unit): PadFrameAnalyzer {
        return PadFrameAnalyzer(
            faceDetector = faceDetector,
            textureAnalyzer = textureAnalyzer,
            depthAnalyzer = depthAnalyzer,
            deviceDetector = deviceDetector,
            nativeChallengeManager = nativeChallengeManager,
            config = config,
            onResult = onResult
        )
    }

    /** Create a challenge manager for the challenge-response flow. */
    fun createChallengeManager(): ChallengeManager = nativeChallengeManager

    /** Release all resources. */
    fun close() {
        faceDetector.close()
        textureAnalyzer.close()
        depthAnalyzer.close()
        deviceDetector.close()
        embeddingAnalyzer.close()
        Timber.tag(TAG).d("Pipeline closed")
    }

    companion object {
        private const val TAG = "PAD"

        /**
         * Build a fully-initialized pipeline.
         * Must be called on a background thread (model loading blocks).
         */
        fun create(context: Context, config: PadConfig = PadConfig.Default): PadPipeline {
            Timber.tag(TAG).d("Creating pipeline...")
            val appContext = context.applicationContext

            val faceDetector = MediaPipeFaceDetector(appContext)
            val textureAnalyzer = MiniFasNetAnalyzer(appContext)
            val depthModels = CdcnDepthAnalyzer(appContext)
            val depthAnalyzer = CascadedDepthAnalyzer(depthModels, config)
            val deviceDetector = SsdDeviceDetector(appContext)
            val embeddingAnalyzer = MobileFaceNetAnalyzer(appContext)
            val nativeChallengeManager = NativeChallengeManager()

            OpenPadNative.nativeInit(OpenPadNative.configToBytes(config))

            Timber.tag(TAG).d("Pipeline created")
            return PadPipeline(
                faceDetector = faceDetector,
                textureAnalyzer = textureAnalyzer,
                depthAnalyzer = depthAnalyzer,
                depthModels = depthModels,
                deviceDetector = deviceDetector,
                embeddingAnalyzer = embeddingAnalyzer,
                nativeChallengeManager = nativeChallengeManager,
                config = config
            )
        }
    }
}
