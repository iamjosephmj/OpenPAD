package com.openpad.core.embedding

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * MobileFaceNet face embedding extractor.
 *
 * Produces a 192-dimensional L2-normalized embedding from a face crop.
 * Used for face consistency verification: compare embeddings across challenge
 * phases to detect face swaps mid-session.
 *
 * Input: [2, 112, 112, 3] RGB [-1,1] NHWC (fixed batch=2).
 * Output: [2, 192] float embedding per image.
 *
 * Model: MobileFaceNet (~5MB, ~10ms on mobile CPU).
 * Source: syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing (MIT license).
 */
class MobileFaceNetAnalyzer(context: Context) : FaceEmbeddingAnalyzer {

    private val interpreter: Interpreter?
    private val isPlaceholder: Boolean

    /** Pre-allocated buffer for batch=2 input: 2 × 112 × 112 × 3 × 4 bytes. */
    private val inputBuffer: ByteBuffer

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
            ByteBuffer.allocateDirect(BATCH_SIZE * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }

        if (isPlaceholder) {
            Timber.tag(TAG).w("MobileFaceNet model not found — face consistency check disabled")
        } else {
            Timber.tag(TAG).d("MobileFaceNet loaded (batch=2, 112x112 -> 192-dim)")
        }
    }

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FaceEmbeddingResult {
        if (isPlaceholder) return PLACEHOLDER_RESULT

        val crop = cropFace(bitmap, faceBbox)
        inputBuffer.rewind()
        fillSlot(crop)
        // Pad second slot with zeros (model requires batch=2)
        val padSize = INPUT_SIZE * INPUT_SIZE * 3 * 4
        for (i in 0 until padSize step 4) inputBuffer.putFloat(0f)
        inputBuffer.rewind()
        if (crop !== bitmap) crop.recycle()

        val output = Array(BATCH_SIZE) { FloatArray(EMBEDDING_DIM) }
        interpreter!!.run(inputBuffer, output)

        val embedding = l2Normalize(output[0])
        return FaceEmbeddingResult(embedding = embedding)
    }

    /**
     * Analyze two face crops in a single batch inference.
     * Returns a pair of embeddings (both L2-normalized).
     * Returns null if either crop is null or model is in placeholder mode.
     */
    fun analyzePair(
        bitmapA: Bitmap, bboxA: FaceDetection.BBox,
        bitmapB: Bitmap, bboxB: FaceDetection.BBox
    ): Pair<FaceEmbeddingResult, FaceEmbeddingResult>? {
        if (isPlaceholder) return null

        val cropA = cropFace(bitmapA, bboxA)
        val cropB = cropFace(bitmapB, bboxB)

        inputBuffer.rewind()
        fillSlot(cropA)
        fillSlot(cropB)
        inputBuffer.rewind()

        if (cropA !== bitmapA) cropA.recycle()
        if (cropB !== bitmapB) cropB.recycle()

        val output = Array(BATCH_SIZE) { FloatArray(EMBEDDING_DIM) }
        interpreter!!.run(inputBuffer, output)

        return Pair(
            FaceEmbeddingResult(embedding = l2Normalize(output[0])),
            FaceEmbeddingResult(embedding = l2Normalize(output[1]))
        )
    }

    override fun close() {
        interpreter?.close()
    }

    private fun cropFace(bitmap: Bitmap, faceBbox: FaceDetection.BBox): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height

        // Face center and size in pixel coordinates
        val faceCX = (faceBbox.left + faceBbox.right) / 2f * imgW
        val faceCY = (faceBbox.top + faceBbox.bottom) / 2f * imgH
        val faceW = faceBbox.width() * imgW * CROP_MARGIN
        val faceH = faceBbox.height() * imgH * CROP_MARGIN

        // Square crop (take the larger dimension)
        val side = maxOf(faceW, faceH)

        val left = (faceCX - side / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - side / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + side / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + side / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
    }

    /** Write one 112x112 image into the current inputBuffer position. */
    private fun fillSlot(bitmap: Bitmap) {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            // Normalize to [-1, 1]: (value - 128) / 128
            inputBuffer.putFloat(((pixel shr 16 and 0xFF) - 128f) / 128f) // R
            inputBuffer.putFloat(((pixel shr 8 and 0xFF) - 128f) / 128f)  // G
            inputBuffer.putFloat(((pixel and 0xFF) - 128f) / 128f)         // B
        }
    }

    companion object {
        private const val TAG = "PAD"
        private const val MODEL_PATH = "models/face_embedding.pad"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_DIM = 192
        private const val BATCH_SIZE = 2
        private const val CROP_MARGIN = 1.3f

        private val PLACEHOLDER_RESULT = FaceEmbeddingResult(embedding = null)

        fun l2Normalize(vec: FloatArray): FloatArray {
            var norm = 0f
            for (v in vec) norm += v * v
            norm = sqrt(norm)
            if (norm < 1e-10f) return vec
            return FloatArray(vec.size) { vec[it] / norm }
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom > 1e-10f) dot / denom else 0f
        }
    }
}
