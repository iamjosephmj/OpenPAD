package com.openpad.core.device

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Detects screen-based presentation-attack indicators using a YOLOv5n model
 * trained on 5 PAD-specific classes.
 *
 * Model trained on 384x384 images with classes:
 *   0=artifact, 1=device, 2=face, 3=finger, 4=reflection
 *
 * The detector runs float32 inference, decodes raw YOLOv5 output tensors,
 * applies confidence thresholding and NMS, then computes a composite spoof
 * score based on which spoof-indicating classes overlap the face region.
 *
 * Falls back to a no-op placeholder if the model asset is missing.
 */
class YoloScreenReflectionDetector(context: Context) : ScreenReflectionDetector {

    private val interpreter: Interpreter?
    private val isPlaceholder: Boolean
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
        Log.d(TAG, "init placeholder=$isPlaceholder")

        inputBuffer = if (!isPlaceholder) {
            ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }
    }

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): ScreenReflectionResult {
        if (isPlaceholder) return PLACEHOLDER_RESULT

        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        fillInputBuffer(scaled)
        if (scaled !== bitmap) scaled.recycle()

        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val numDetections = outputShape[1]
        val numValues = outputShape[2]
        val rawOutput = Array(1) { Array(numDetections) { FloatArray(numValues) } }

        interpreter.run(inputBuffer, rawOutput)

        val detections = decodeAndNms(rawOutput[0], numDetections, numValues)
        if (detections.isNotEmpty()) {
            val summary = detections.joinToString { "${CLASS_NAMES[it.classId]}:${"%.2f".format(it.confidence)}" }
            Log.d(TAG, "detections(${detections.size}): $summary")
        }
        return buildResult(detections, faceBbox)
    }

    override fun close() {
        interpreter?.close()
    }

    // -- Preprocessing --

    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f) // R
            inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)  // G
            inputBuffer.putFloat((pixel and 0xFF) / 255f)         // B
        }
        inputBuffer.rewind()
    }

    // -- Post-processing --

    private data class Detection(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val confidence: Float, val classId: Int
    )

    /**
     * Decode raw YOLOv5 output [N, 5+numClasses] into filtered + NMS'd detections.
     * Each row: [cx, cy, w, h, objConf, cls0, cls1, ..., cls5]
     */
    private fun decodeAndNms(
        raw: Array<FloatArray>,
        numAnchors: Int,
        numValues: Int
    ): List<Detection> {
        val numClasses = numValues - 5
        val candidates = mutableListOf<Detection>()

        for (i in 0 until numAnchors) {
            val row = raw[i]
            val objConf = row[4]
            if (objConf < OBJ_CONF_THRESHOLD) continue

            var bestClassScore = 0f
            var bestClassId = 0
            for (c in 0 until numClasses) {
                val score = row[5 + c]
                if (score > bestClassScore) {
                    bestClassScore = score
                    bestClassId = c
                }
            }

            val conf = objConf * bestClassScore
            if (conf < CONF_THRESHOLD) continue

            val cx = row[0] / INPUT_SIZE
            val cy = row[1] / INPUT_SIZE
            val w = row[2] / INPUT_SIZE
            val h = row[3] / INPUT_SIZE
            val x1 = (cx - w / 2f).coerceIn(0f, 1f)
            val y1 = (cy - h / 2f).coerceIn(0f, 1f)
            val x2 = (cx + w / 2f).coerceIn(0f, 1f)
            val y2 = (cy + h / 2f).coerceIn(0f, 1f)

            candidates.add(Detection(x1, y1, x2, y2, conf, bestClassId))
        }

        candidates.sortByDescending { it.confidence }
        return nms(candidates, NMS_IOU_THRESHOLD)
    }

    private fun nms(sorted: List<Detection>, iouThreshold: Float): List<Detection> {
        val keep = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            keep.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (sorted[i].classId == sorted[j].classId &&
                    iou(sorted[i], sorted[j]) > iouThreshold
                ) {
                    suppressed[j] = true
                }
            }
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ix1 = maxOf(a.x1, b.x1)
        val iy1 = maxOf(a.y1, b.y1)
        val ix2 = minOf(a.x2, b.x2)
        val iy2 = minOf(a.y2, b.y2)
        val iw = (ix2 - ix1).coerceAtLeast(0f)
        val ih = (iy2 - iy1).coerceAtLeast(0f)
        val inter = iw * ih
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val union = areaA + areaB - inter
        return if (union > 0f) inter / union else 0f
    }

    // -- Result building --

    private fun buildResult(
        detections: List<Detection>,
        faceBbox: FaceDetection.BBox
    ): ScreenReflectionResult {
        var reflectionDetected = false
        var artifactDetected = false
        var fingerDetected = false
        var faceOnScreenDetected = false
        var deviceDetected = false
        var maxConf = 0f

        val spoofClasses = mutableSetOf<Int>()

        for (det in detections) {
            if (det.confidence > maxConf) maxConf = det.confidence

            val overlaps = bboxOverlap(
                det.x1, det.y1, det.x2, det.y2,
                faceBbox.left, faceBbox.top, faceBbox.right, faceBbox.bottom
            ) > OVERLAP_THRESHOLD

            when (det.classId) {
                CLASS_ARTIFACT -> {
                    artifactDetected = true
                    if (overlaps) spoofClasses.add(CLASS_ARTIFACT)
                }
                CLASS_DEVICE -> {
                    deviceDetected = true
                    spoofClasses.add(CLASS_DEVICE)
                }
                CLASS_FACE -> {
                    faceOnScreenDetected = true
                }
                CLASS_FINGER -> {
                    fingerDetected = true
                    spoofClasses.add(CLASS_FINGER)
                }
                CLASS_REFLECTION -> {
                    reflectionDetected = true
                    if (overlaps) spoofClasses.add(CLASS_REFLECTION)
                }
            }
        }

        val signalCount = spoofClasses.size
        val spoofScore = when {
            signalCount == 0 -> 0f
            signalCount == 1 -> (maxConf * 0.5f).coerceAtMost(0.6f)
            signalCount >= 2 -> (maxConf * 0.8f + signalCount * 0.1f).coerceAtMost(1f)
            else -> 0f
        }

        val result = ScreenReflectionResult(
            reflectionDetected = reflectionDetected,
            artifactDetected = artifactDetected,
            fingerDetected = fingerDetected,
            faceOnScreenDetected = faceOnScreenDetected,
            deviceDetected = deviceDetected,
            maxConfidence = maxConf,
            spoofSignalCount = signalCount,
            spoofScore = spoofScore
        )
        Log.d(TAG, "result: signals=$signalCount maxConf=${"%.3f".format(maxConf)} " +
            "spoofScore=${"%.3f".format(spoofScore)} " +
            "[art=$artifactDetected dev=$deviceDetected ref=$reflectionDetected " +
            "fin=$fingerDetected face=$faceOnScreenDetected]")
        return result
    }

    private fun bboxOverlap(
        ax1: Float, ay1: Float, ax2: Float, ay2: Float,
        bx1: Float, by1: Float, bx2: Float, by2: Float
    ): Float {
        val ix1 = maxOf(ax1, bx1)
        val iy1 = maxOf(ay1, by1)
        val ix2 = minOf(ax2, bx2)
        val iy2 = minOf(ay2, by2)
        val iw = (ix2 - ix1).coerceAtLeast(0f)
        val ih = (iy2 - iy1).coerceAtLeast(0f)
        val inter = iw * ih
        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)
        val union = areaA + areaB - inter
        return if (union > 0f) inter / union else 0f
    }

    companion object {
        private const val TAG = "DA8966"
        private const val MODEL_PATH = "models/screen_reflection.pad"
        private const val INPUT_SIZE = 384
        private const val OBJ_CONF_THRESHOLD = 0.25f
        private const val CONF_THRESHOLD = 0.4f
        private const val NMS_IOU_THRESHOLD = 0.45f
        private const val OVERLAP_THRESHOLD = 0.05f

        private const val CLASS_ARTIFACT = 0
        private const val CLASS_DEVICE = 1
        private const val CLASS_FACE = 2
        private const val CLASS_FINGER = 3
        private const val CLASS_REFLECTION = 4

        private val CLASS_NAMES = arrayOf("artifact", "device", "face", "finger", "reflection")

        val PLACEHOLDER_RESULT = ScreenReflectionResult(
            reflectionDetected = false,
            artifactDetected = false,
            fingerDetected = false,
            faceOnScreenDetected = false,
            deviceDetected = false,
            maxConfidence = 0f,
            spoofSignalCount = 0,
            spoofScore = 0f
        )
    }
}
