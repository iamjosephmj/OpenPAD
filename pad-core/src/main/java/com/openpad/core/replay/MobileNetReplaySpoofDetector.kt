package com.openpad.core.replay

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MobileNetV2-based replay-attack spoof detector.
 *
 * Classifies face regions as live or replayed-on-screen by detecting
 * screen artifacts (moiré, color shifts, reduced dynamic range).
 *
 * Model: replay_spoof.pad (~4.6 MB, float16 quantized)
 * Input: [1, 224, 224, 3] float32, MobileNetV2-preprocessed (pixels in [-1, 1])
 * Output: [1, 1] float32, sigmoid (0 = live, 1 = spoof)
 */
class MobileNetReplaySpoofDetector(context: Context) : ReplaySpoofDetector {

    private val interpreter: Interpreter?
    private val isPlaceholder: Boolean
    private val inputBuffer: ByteBuffer
    private val outputBuffer: Array<FloatArray>

    init {
        val appContext = context.applicationContext
        interpreter = try {
            val model = ModelLoader.loadFromAssets(appContext, MODEL_PATH)
            Interpreter(model, ModelLoader.createOptions(threads = 2, useGpu = false))
        } catch (_: Exception) {
            null
        }

        isPlaceholder = interpreter == null

        inputBuffer = if (!isPlaceholder) {
            ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * FLOAT_BYTES).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }

        outputBuffer = Array(1) { FloatArray(1) }
    }

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): ReplaySpoofResult {
        if (isPlaceholder) return PLACEHOLDER_RESULT

        // Use the full frame — matches training where full video frames were used.
        // The model learned to detect screen edges, bezels, moiré in the full scene.
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        fillInputBuffer(scaled)
        if (scaled !== bitmap) scaled.recycle()

        outputBuffer[0][0] = 0f
        interpreter!!.run(inputBuffer, outputBuffer)

        val spoofScore = outputBuffer[0][0].coerceIn(0f, 1f)
        return ReplaySpoofResult(
            spoofScore = spoofScore,
            isSpoof = spoofScore >= SPOOF_THRESHOLD
        )
    }

    override fun close() {
        interpreter?.close()
    }

    /** Fill the input buffer with MobileNetV2-preprocessed pixel values: (pixel / 127.5) - 1.0 */
    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            inputBuffer.putFloat(r / 127.5f - 1f)
            inputBuffer.putFloat(g / 127.5f - 1f)
            inputBuffer.putFloat(b / 127.5f - 1f)
        }
        inputBuffer.rewind()
    }

    companion object {
        private const val MODEL_PATH = "models/replay_spoof.pad"
        private const val INPUT_SIZE = 224
        private const val FLOAT_BYTES = 4
        private const val SPOOF_THRESHOLD = 0.5f

        private val PLACEHOLDER_RESULT = ReplaySpoofResult(
            spoofScore = 0f,
            isSpoof = false
        )
    }
}
