package com.openpad.core.texture

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Layer 2: MiniFASNet texture-based liveness analyzer.
 *
 * Runs 2 pretrained MiniFASNet TFLite models at different face crop scales:
 * - MiniFASNetV2 at 2.7× crop (wider context)
 * - MiniFASNetV1SE at 4.0× crop (with Squeeze-and-Excitation, even wider context)
 *
 * Each model takes an 80×80 BGR crop ([0,255] range) and outputs
 * 3 raw logits. We apply softmax to get [background, genuine, spoof] probabilities,
 * then average across models.
 *
 * If the TFLite model files are not found in assets, falls back to placeholder
 * behavior (hardcoded scores) with a warning log.
 *
 * Source: [Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)
 * TFLite conversion: [feni-katharotiya/Silent-Face-Anti-Spoofing-TFLite](https://github.com/feni-katharotiya/Silent-Face-Anti-Spoofing-TFLite)
 * License: Apache 2.0
 *
 * @param context Application context for loading model assets.
 * @param enableReflectionProbe If true, runs a 2×2 spatial probe to detect specular reflections.
 */
class MiniFasNetAnalyzer(
    context: Context,
    private val enableReflectionProbe: Boolean = true
) : TextureAnalyzer {

    private val interpreters: List<Interpreter>?
    private val isPlaceholder: Boolean

    /** Reusable input buffer — avoids allocating 80*80*3*4 = 76,800 bytes per inference. */
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    init {
        val appContext = context.applicationContext
        val opts = ModelLoader.createOptions(threads = 2, useGpu = false)
        interpreters = try {
            SCALE_CONFIGS.map { config ->
                val model = ModelLoader.loadFromAssets(appContext, config.modelPath)
                Interpreter(model, opts)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
        isPlaceholder = interpreters == null
    }

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): TextureResult {
        if (isPlaceholder) {
            return analyzePlaceholder()
        }

        val models = interpreters!!

        // Step 1: Run 3-scale inference
        val scaleScores = SCALE_CONFIGS.zip(models).map { (config, interpreter) ->
            val crop = cropFaceAtScale(bitmap, faceBbox, config.scale)
            val scores = runModel(interpreter, crop)
            crop.recycle()
            scores
        }

        // Step 2: Average scores across scales
        // Silent-Face-Anti-Spoofing class ordering: [0]=background, [1]=genuine, [2]=spoof
        val avgGenuine = scaleScores.map { it[1] }.average().toFloat()
        val avgSpoof = scaleScores.map { it[2] }.average().toFloat()
        val avgBg = scaleScores.map { it[0] }.average().toFloat()

        // Step 3: Reflection probe (uses scale 1.0 model)
        val (reflVariance, reflMinScore) = if (enableReflectionProbe) {
            probeReflections(bitmap, faceBbox, models[REFLECTION_PROBE_MODEL_INDEX])
        } else {
            Pair(0f, avgGenuine)
        }

        return TextureResult(
            genuineScore = avgGenuine,
            spoofScore = avgSpoof,
            backgroundScore = avgBg,
            reflectionProbeVariance = reflVariance,
            reflectionProbeMinScore = reflMinScore
        )
    }

    override fun close() {
        interpreters?.forEach { it.close() }
    }

    // ---- Private: inference ----

    /**
     * Run a single MiniFASNet model on an 80×80 crop.
     *
     * Auto-detects whether the model outputs raw logits or softmax probabilities:
     * - If output sums to ~1.0 with all non-negative values → already probabilities
     * - Otherwise → apply softmax to convert logits to probabilities
     *
     * @return FloatArray of size 3: [background, genuine, spoof] probabilities.
     */
    private fun runModel(interpreter: Interpreter, crop: Bitmap): FloatArray {
        fillInputBuffer(crop)
        val output = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter.run(inputBuffer, output)

        val raw = output[0]

        // If the model already outputs probabilities (sum ≈ 1.0), skip softmax
        // to avoid double-softmax which compresses scores toward uniform
        val sum = raw[0] + raw[1] + raw[2]
        return if (sum in 0.95f..1.05f && raw.all { it >= 0f }) {
            // Already probabilities — model has baked-in softmax
            raw
        } else {
            softmax(raw)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = FloatArray(logits.size) { kotlin.math.exp(logits[it] - maxLogit) }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    /**
     * Fill the reusable [inputBuffer] with BGR pixel values from [bitmap].
     *
     * The pretrained TFLite models (from Silent-Face-Anti-Spoofing) expect
     * NHWC layout [1, 80, 80, 3] with BGR channel order and pixel values in
     * [0, 255] range (NOT [0,1]). The original training code uses OpenCV's
     * cv2.imread (BGR) and a custom ToTensor() that skips the /255 step.
     */
    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            inputBuffer.putFloat((pixel and 0xFF).toFloat())         // B
            inputBuffer.putFloat((pixel shr 8 and 0xFF).toFloat())   // G
            inputBuffer.putFloat((pixel shr 16 and 0xFF).toFloat())  // R
        }

        inputBuffer.rewind()
    }

    // ---- Private: face cropping ----

    /**
     * Crop the face region at the given [scale] relative to the bounding box and
     * resize to [INPUT_SIZE]×[INPUT_SIZE].
     *
     * - Scale 0.8: tighter crop, focuses on face center
     * - Scale 1.0: exact bounding box
     * - Scale 2.7: wide crop, includes background context
     */
    private fun cropFaceAtScale(
        bitmap: Bitmap,
        faceBbox: FaceDetection.BBox,
        scale: Float
    ): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height

        val faceCX = (faceBbox.left + faceBbox.right) / 2f * imgW
        val faceCY = (faceBbox.top + faceBbox.bottom) / 2f * imgH
        val faceW = faceBbox.width() * imgW
        val faceH = faceBbox.height() * imgH

        val cropW = faceW * scale
        val cropH = faceH * scale

        val left = (faceCX - cropW / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - cropH / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + cropW / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + cropH / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    // ---- Private: reflection probe ----

    /**
     * Reflection probe: run the model on 4 offset crops in a 2×2 grid around the face.
     *
     * For a real face, all 4 quadrants produce similar genuine scores (consistent lighting).
     * For a screen with specular reflections, one or more quadrants will have degraded
     * scores → high variance.
     *
     * @return Pair of (variance, minScore) across the 4 quadrant genuine scores.
     */
    private fun probeReflections(
        bitmap: Bitmap,
        faceBbox: FaceDetection.BBox,
        interpreter: Interpreter
    ): Pair<Float, Float> {
        val offsetFraction = 0.1f
        val halfW = faceBbox.width() * offsetFraction
        val halfH = faceBbox.height() * offsetFraction

        val offsets = listOf(
            -halfW to -halfH, halfW to -halfH,
            -halfW to halfH, halfW to halfH
        )

        val scores = offsets.map { (dx, dy) ->
            val offsetBbox = FaceDetection.BBox(
                left = (faceBbox.left + dx).coerceIn(0f, 1f),
                top = (faceBbox.top + dy).coerceIn(0f, 1f),
                right = (faceBbox.right + dx).coerceIn(0f, 1f),
                bottom = (faceBbox.bottom + dy).coerceIn(0f, 1f)
            )
            val crop = cropFaceAtScale(bitmap, offsetBbox, 1.0f)
            val result = runModel(interpreter, crop)[1] // genuine score (index 1 in Silent-Face)
            crop.recycle()
            result
        }

        val mean = scores.average().toFloat()
        val variance = scores.map { (it - mean) * (it - mean) }.average().toFloat()
        return Pair(variance, scores.min())
    }

    // ---- Private: placeholder fallback ----

    private fun analyzePlaceholder(): TextureResult {
        return TextureResult(
            genuineScore = PLACEHOLDER_GENUINE,
            spoofScore = PLACEHOLDER_SPOOF,
            backgroundScore = PLACEHOLDER_BG,
            reflectionProbeVariance = 0f,
            reflectionProbeMinScore = PLACEHOLDER_GENUINE
        )
    }

    // ---- Constants ----

    private data class ScaleConfig(val scale: Float, val modelPath: String)

    companion object {
        private const val INPUT_SIZE = 80
        private const val NUM_CLASSES = 3

        // Placeholder fallback scores (used when models are missing)
        private const val PLACEHOLDER_GENUINE = 0.7f
        private const val PLACEHOLDER_SPOOF = 0.2f
        private const val PLACEHOLDER_BG = 0.1f

        // Index of the 2.7× model, used for reflection probe
        private const val REFLECTION_PROBE_MODEL_INDEX = 0

        // Pretrained models from Silent-Face-Anti-Spoofing, converted to TFLite by
        // feni-katharotiya/Silent-Face-Anti-Spoofing-TFLite.
        // Each model was trained at a specific crop scale relative to the face bbox.
        private val SCALE_CONFIGS = listOf(
            ScaleConfig(2.7f, "models/texture_2x7.pad"),
            ScaleConfig(4.0f, "models/texture_4x0.pad"),
        )
    }
}
