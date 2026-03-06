package com.openpad.core

import android.content.Context
import com.openpad.core.analyzer.FramePreprocessor
import com.openpad.core.analyzer.PadFrameAnalyzer
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.depth.CascadedDepthAnalyzer
import com.openpad.core.depth.CdcnDepthAnalyzer
import com.openpad.core.depth.DepthAnalyzer
import com.openpad.core.detection.FaceDetector
import com.openpad.core.detection.MediaPipeFaceDetector
import com.openpad.core.device.ScreenReflectionDetector
import com.openpad.core.device.YoloScreenReflectionDetector
import com.openpad.core.embedding.FaceEmbeddingAnalyzer
import com.openpad.core.embedding.MobileFaceNetAnalyzer
import com.openpad.core.enhance.EspcnFrameEnhancer
import com.openpad.core.enhance.FrameEnhancer
import com.openpad.core.ndk.NativeChallengeManager
import com.openpad.core.ndk.OpenPadNative
import com.openpad.core.texture.MiniFasNetAnalyzer
import com.openpad.core.texture.TextureAnalyzer

/**
 * Entry point for the Open-PAD pipeline.
 *
 * Creates all components with manual construction (no DI framework).
 * FFT, LBP, photometric, temporal, aggregation, stabilizer, and challenge logic
 * run in the native C layer (NDK). TFLite models (face, texture, depth,
 * embedding) remain in Kotlin.
 */
class PadPipeline private constructor(
    val faceDetector: FaceDetector,
    val textureAnalyzer: TextureAnalyzer,
    val depthAnalyzer: DepthAnalyzer,
    val depthModels: CdcnDepthAnalyzer,
    val screenReflectionDetector: ScreenReflectionDetector,
    override val embeddingAnalyzer: FaceEmbeddingAnalyzer,
    val frameEnhancer: FrameEnhancer,
    internal val nativeChallengeManager: NativeChallengeManager,
    override val config: InternalPadConfig
) : PadPipelineContract {
    /** Create a CameraX ImageAnalysis.Analyzer wired to the full pipeline. */
    override fun createFrameAnalyzer(onResult: (PadResult) -> Unit): PadFrameAnalyzer {
        val preprocessor = if (config.enablePreprocessing) {
            FramePreprocessor(
                gammaTarget = config.preprocessingGammaTarget,
                claheClipLimit = config.preprocessingClaheClipLimit
            )
        } else {
            null
        }

        return PadFrameAnalyzer(
            faceDetector = faceDetector,
            textureAnalyzer = textureAnalyzer,
            depthAnalyzer = depthAnalyzer,
            screenReflectionDetector = screenReflectionDetector,
            frameEnhancer = frameEnhancer,
            framePreprocessor = preprocessor,
            nativeChallengeManager = nativeChallengeManager,
            config = config,
            onResult = onResult
        )
    }

    /** Create a challenge manager for the challenge-response flow. */
    override fun createChallengeManager(): ChallengeManager = nativeChallengeManager

    /** Release all resources. */
    override fun close() {
        faceDetector.close()
        textureAnalyzer.close()
        depthAnalyzer.close()
        screenReflectionDetector.close()
        embeddingAnalyzer.close()
        frameEnhancer.close()
        OpenPadNative.nativeDestroy()
    }

    companion object {
        /**
         * Build a fully-initialized pipeline.
         * Must be called on a background thread (model loading blocks).
         */
        fun create(context: Context, config: InternalPadConfig = InternalPadConfig.Default): PadPipeline {
            val appContext = context.applicationContext

            val faceDetector = MediaPipeFaceDetector(appContext)
            val textureAnalyzer = MiniFasNetAnalyzer(appContext)
            val depthModels = CdcnDepthAnalyzer(appContext)
            val depthAnalyzer = CascadedDepthAnalyzer(depthModels, config)
            val screenReflectionDetector = YoloScreenReflectionDetector(appContext)
            val embeddingAnalyzer = MobileFaceNetAnalyzer(appContext)
            val frameEnhancer = EspcnFrameEnhancer(appContext)
            val nativeChallengeManager = NativeChallengeManager()

            OpenPadNative.nativeInit(OpenPadNative.configToBytes(config))

            return PadPipeline(
                faceDetector = faceDetector,
                textureAnalyzer = textureAnalyzer,
                depthAnalyzer = depthAnalyzer,
                depthModels = depthModels,
                screenReflectionDetector = screenReflectionDetector,
                embeddingAnalyzer = embeddingAnalyzer,
                frameEnhancer = frameEnhancer,
                nativeChallengeManager = nativeChallengeManager,
                config = config
            )
        }
    }
}
