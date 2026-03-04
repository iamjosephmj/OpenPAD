package com.openpad.core.detection

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Layer 1: MediaPipe BlazeFace short-range face detector.
 *
 * Model: face_detection.pad (Apache 2.0, ~230KB)
 * Input: 128x128 RGB float tensor
 * Output: 896 anchor boxes with scores + 16 regressors (bbox + 6 keypoints)
 *
 * Decoding follows the MediaPipe SSD anchor specification:
 * - 2 strides (8, 16) generating 896 total anchors
 * - Bounding box: center_x, center_y, width, height (relative to anchor)
 * - 6 keypoints: x, y pairs after the bbox values
 * - Score: raw logit → sigmoid
 */
class MediaPipeFaceDetector(context: Context) : FaceDetector {

    private val interpreter: Interpreter
    private val anchors: List<Anchor>

    init {
        val model = ModelLoader.loadFromAssets(context, MODEL_PATH)
        interpreter = Interpreter(model, ModelLoader.createOptions(threads = 2, useGpu = false))
        anchors = generateAnchors()
    }

    override fun detect(bitmap: Bitmap): FaceDetection? {
        val inputBuffer = preprocessBitmap(bitmap)

        // Allocate output buffers
        // Output 0: [1, 896, 16] — regressors (bbox + keypoints)
        val regressors = Array(1) { Array(NUM_ANCHORS) { FloatArray(NUM_REGRESSORS) } }
        // Output 1: [1, 896, 1] — classification scores
        val scores = Array(1) { Array(NUM_ANCHORS) { FloatArray(1) } }

        val outputs = mapOf(0 to regressors, 1 to scores)
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        // Decode detections
        val detections = mutableListOf<FaceDetection>()
        for (i in 0 until NUM_ANCHORS) {
            val score = sigmoid(scores[0][i][0])
            if (score < MIN_SCORE_THRESHOLD) continue

            val anchor = anchors[i]
            val reg = regressors[0][i]

            // Decode bbox (center format → corner format)
            val cx = reg[0] / INPUT_SIZE * anchor.w + anchor.cx
            val cy = reg[1] / INPUT_SIZE * anchor.h + anchor.cy
            val w = reg[2] / INPUT_SIZE * anchor.w
            val h = reg[3] / INPUT_SIZE * anchor.h

            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h / 2f

            val bbox = FaceDetection.BBox(
                left = left.coerceIn(0f, 1f),
                top = top.coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = bottom.coerceIn(0f, 1f)
            )

            // Decode 6 keypoints
            val keypoints = (0 until NUM_KEYPOINTS).map { k ->
                val kx = reg[4 + k * 2] / INPUT_SIZE * anchor.w + anchor.cx
                val ky = reg[4 + k * 2 + 1] / INPUT_SIZE * anchor.h + anchor.cy
                FaceDetection.PointF(kx.coerceIn(0f, 1f), ky.coerceIn(0f, 1f))
            }

            detections.add(FaceDetection(score, bbox, keypoints))
        }

        if (detections.isEmpty()) return null

        // NMS and return highest confidence
        val nmsDetections = nonMaxSuppression(detections, NMS_IOU_THRESHOLD)
        return nmsDetections.maxByOrNull { it.confidence }
    }

    override fun close() {
        interpreter.close()
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) scaled.recycle()

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) / 127.5f) - 1f) // R
            buffer.putFloat(((pixel shr 8 and 0xFF) / 127.5f) - 1f)  // G
            buffer.putFloat(((pixel and 0xFF) / 127.5f) - 1f)         // B
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Generate SSD anchors following MediaPipe's BlazeFace specification.
     * 2 strides: 8 (2 anchors per cell) and 16 (6 anchors per cell).
     * Total: (16*16*2) + (8*8*6) = 512 + 384 = 896 anchors.
     */
    private fun generateAnchors(): List<Anchor> {
        val anchors = mutableListOf<Anchor>()

        // Stride 8: 16x16 grid, 2 anchors per cell
        val gridSize8 = INPUT_SIZE / 8  // 16
        for (y in 0 until gridSize8) {
            for (x in 0 until gridSize8) {
                val cx = (x + 0.5f) / gridSize8
                val cy = (y + 0.5f) / gridSize8
                repeat(2) {
                    anchors.add(Anchor(cx, cy, 1f, 1f))
                }
            }
        }

        // Stride 16: 8x8 grid, 6 anchors per cell
        val gridSize16 = INPUT_SIZE / 16  // 8
        for (y in 0 until gridSize16) {
            for (x in 0 until gridSize16) {
                val cx = (x + 0.5f) / gridSize16
                val cy = (y + 0.5f) / gridSize16
                repeat(6) {
                    anchors.add(Anchor(cx, cy, 1f, 1f))
                }
            }
        }

        return anchors
    }

    private fun nonMaxSuppression(
        detections: List<FaceDetection>,
        iouThreshold: Float
    ): List<FaceDetection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<FaceDetection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.bbox, it.bbox) > iouThreshold }
        }

        return result
    }

    private fun iou(a: FaceDetection.BBox, b: FaceDetection.BBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()
        val unionArea = aArea + bArea - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private data class Anchor(val cx: Float, val cy: Float, val w: Float, val h: Float)

    companion object {
        private const val MODEL_PATH = "models/face_detection.pad"
        private const val INPUT_SIZE = 128
        private const val NUM_ANCHORS = 896
        private const val NUM_REGRESSORS = 16 // 4 bbox + 6*2 keypoints
        private const val NUM_KEYPOINTS = 6
        private const val MIN_SCORE_THRESHOLD = 0.5f
        private const val NMS_IOU_THRESHOLD = 0.3f
    }
}
