package com.openpad.core.depth

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result from a single CDCN inference pass.
 *
 * @param depthScore Clamped mean of depth map [0..1].
 * @param variance Variance placeholder (reserved for future quadrant variance use).
 * @param rawMean Raw mean of depth map (unclamped).
 * @param characteristics Full depth statistics computed from the 32x32 map.
 */
internal data class CdcnInferenceResult(
    val depthScore: Float,
    val variance: Float,
    val rawMean: Float,
    val characteristics: DepthCharacteristics
)

/**
 * Low-level model runner for MN3 (fast binary classifier) and CDCN (depth map).
 *
 * Loads both models independently. Exposes [analyzeMn3] and [analyzeCdcn] as
 * separate methods so [CascadedDepthAnalyzer] can orchestrate the cascade.
 *
 * Each model has its own [ByteBuffer] for thread safety — MN3 runs on the
 * frame-analysis thread while CDCN runs on a background executor.
 *
 * Model sources:
 * - CDCN: Yu et al., CVPR 2020 (trained, see models/train_cdcn.py)
 * - Anti-Spoof-MN3: PINTO Model Zoo #191, MobileNetV3, ACER 3.81% on CelebA-Spoof
 */
class CdcnDepthAnalyzer(context: Context) : DepthAnalyzer {

    private val cdcnInterpreter: Interpreter?
    private val mn3Interpreter: Interpreter?

    private val mn3InputBuffer: ByteBuffer
    private val cdcnInputBuffer: ByteBuffer

    val hasCdcn: Boolean get() = cdcnInterpreter != null
    val hasMn3: Boolean get() = mn3Interpreter != null

    init {
        val appContext = context.applicationContext

        // Load CDCN (best accuracy, slow ~1200ms)
        cdcnInterpreter = try {
            val options = ModelLoader.createOptions(threads = 4, useGpu = true)
            val model = ModelLoader.loadFromAssets(appContext, CDCN_MODEL_PATH)
            Interpreter(model, options)
        } catch (_: Exception) {
            null
        }

        // Load MN3 (fast ~50ms) — always attempt, independent of CDCN
        mn3Interpreter = try {
            val options = ModelLoader.createOptions(threads = 2, useGpu = false)
            val model = ModelLoader.loadFromAssets(appContext, MN3_MODEL_PATH)
            Interpreter(model, options)
        } catch (_: Exception) {
            null
        }

        // Separate input buffers for thread safety
        mn3InputBuffer = if (mn3Interpreter != null) {
            ByteBuffer.allocateDirect(MN3_INPUT_SIZE * MN3_INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }

        cdcnInputBuffer = if (cdcnInterpreter != null) {
            ByteBuffer.allocateDirect(CDCN_INPUT_SIZE * CDCN_INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }

    }

    /**
     * Synchronous analysis running both MN3 + CDCN inline.
     * Used by benchmark and any caller that wants full depth result per frame.
     */
    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): DepthResult {
        val (real, spoof) = analyzeMn3(bitmap, faceBbox)
        return if (hasCdcn) {
            val cdcnResult = analyzeCdcn(bitmap, faceBbox)
            DepthResult.fromBoth(
                mn3Real = real, mn3Spoof = spoof,
                cdcnScore = cdcnResult.depthScore,
                cdcnVariance = cdcnResult.variance,
                cdcnMean = cdcnResult.rawMean,
                characteristics = cdcnResult.characteristics
            )
        } else {
            DepthResult.fromMn3Only(mn3Real = real, mn3Spoof = spoof)
        }
    }

    override fun close() {
        cdcnInterpreter?.close()
        mn3Interpreter?.close()
    }

    // ---- MN3 binary classifier (fast, sync) ----

    /**
     * Run MN3 inference. Returns Pair(realScore, spoofScore).
     * Thread-safe: uses [mn3InputBuffer] which is only accessed from the frame-analysis thread.
     */
    fun analyzeMn3(bitmap: Bitmap, faceBbox: FaceDetection.BBox): Pair<Float, Float> {
        if (mn3Interpreter == null) {
            return Pair(PLACEHOLDER_SCORE, 1f - PLACEHOLDER_SCORE)
        }

        val crop = cropFace(bitmap, faceBbox, MN3_INPUT_SIZE, 1.4f)
        fillInputBufferMn3(crop)

        val output = Array(1) { FloatArray(MN3_NUM_CLASSES) }
        mn3Interpreter.run(mn3InputBuffer, output)

        val scores = softmax(output[0])
        return Pair(scores[0], scores[1])
    }

    // ---- CDCN depth map (slow, async) ----

    /**
     * Run CDCN inference. Returns [CdcnInferenceResult] with depth score, variance, raw mean,
     * and full [DepthCharacteristics] computed from the 32x32 depth map.
     * Thread-safe: uses [cdcnInputBuffer] which is only accessed from the CDCN executor thread.
     */
    internal fun analyzeCdcn(bitmap: Bitmap, faceBbox: FaceDetection.BBox): CdcnInferenceResult {
        if (cdcnInterpreter == null) {
            return CdcnInferenceResult(
                depthScore = PLACEHOLDER_SCORE,
                variance = 0f,
                rawMean = PLACEHOLDER_SCORE,
                characteristics = DepthCharacteristics(
                    mean = PLACEHOLDER_SCORE, standardDeviation = 0f,
                    quadrantVariance = 0f, minDepth = PLACEHOLDER_SCORE, maxDepth = PLACEHOLDER_SCORE
                )
            )
        }

        val crop = cropFace(bitmap, faceBbox, CDCN_INPUT_SIZE, 1.2f)
        fillInputBufferImageNet(crop)

        val output = Array(1) { Array(CDCN_OUTPUT_SIZE) { FloatArray(CDCN_OUTPUT_SIZE) } }
        cdcnInterpreter.run(cdcnInputBuffer, output)

        val characteristics = DepthCharacteristics.fromDepthMap(output[0])

        return CdcnInferenceResult(
            depthScore = characteristics.mean.coerceIn(0f, 1f),
            variance = 0f,
            rawMean = characteristics.mean,
            characteristics = characteristics
        )
    }

    // ---- Input buffer filling ----

    private fun fillInputBufferImageNet(bitmap: Bitmap) {
        cdcnInputBuffer.rewind()
        val size = CDCN_INPUT_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            cdcnInputBuffer.putFloat((r - 0.485f) / 0.229f)
            cdcnInputBuffer.putFloat((g - 0.456f) / 0.224f)
            cdcnInputBuffer.putFloat((b - 0.406f) / 0.225f)
        }
        cdcnInputBuffer.rewind()
    }

    private fun fillInputBufferMn3(bitmap: Bitmap) {
        mn3InputBuffer.rewind()
        val size = MN3_INPUT_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (pixel in pixels) {
            mn3InputBuffer.putFloat((pixel shr 16 and 0xFF).toFloat()) // R
            mn3InputBuffer.putFloat((pixel shr 8 and 0xFF).toFloat())  // G
            mn3InputBuffer.putFloat((pixel and 0xFF).toFloat())         // B
        }
        mn3InputBuffer.rewind()
    }

    // ---- Helpers ----

    private fun cropFace(
        bitmap: Bitmap, faceBbox: FaceDetection.BBox, targetSize: Int, margin: Float
    ): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = (faceBbox.left + faceBbox.right) / 2f * imgW
        val faceCY = (faceBbox.top + faceBbox.bottom) / 2f * imgH
        val faceW = faceBbox.width() * imgW * margin
        val faceH = faceBbox.height() * imgH * margin

        val left = (faceCX - faceW / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - faceH / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + faceW / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + faceH / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = FloatArray(logits.size) { kotlin.math.exp(logits[it] - maxLogit) }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    companion object {
        private const val CDCN_MODEL_PATH = "models/depth_map.pad"
        private const val CDCN_INPUT_SIZE = 256
        private const val CDCN_OUTPUT_SIZE = 32

        private const val MN3_MODEL_PATH = "models/depth_gate.pad"
        private const val MN3_INPUT_SIZE = 128
        private const val MN3_NUM_CLASSES = 2

        private const val PLACEHOLDER_SCORE = 0.5f
    }
}
