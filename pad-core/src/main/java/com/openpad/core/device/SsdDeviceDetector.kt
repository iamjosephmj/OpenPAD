package com.openpad.core.device

import android.content.Context
import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Detects phones, laptops, and TV/monitor screens using SSD MobileNet V1 (COCO).
 *
 * Uses a quantized SSD MobileNet V1 model trained on COCO (80 classes).
 * Filters detections for device-related classes:
 * - 62: tv (also catches monitors and tablets held landscape)
 * - 63: laptop
 * - 67: cell phone (also catches tablets)
 *
 * If the device bounding box overlaps with the face bounding box, it's a strong
 * signal that someone is holding a screen showing a face (replay attack).
 *
 * Model: device_detection.pad (4MB, ~30ms on mobile CPU)
 * Input: 300x300 uint8 RGB NHWC
 * Output (post-NMS): boxes [1,10,4], classes [1,10], scores [1,10], count [1]
 */
class SsdDeviceDetector(context: Context) : DeviceDetector {

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

        inputBuffer = if (!isPlaceholder) {
            ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3).apply {
                order(ByteOrder.nativeOrder())
            }
        } else {
            ByteBuffer.allocateDirect(0)
        }

        // isPlaceholder already set above; no further init needed
    }

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): DeviceDetectionResult {
        if (isPlaceholder) return PLACEHOLDER_RESULT

        // Resize full frame to 300x300
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        fillInputBuffer(scaled)
        if (scaled !== bitmap) scaled.recycle()

        // Run inference
        val boxes = Array(1) { Array(MAX_DETECTIONS) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(MAX_DETECTIONS) }
        val scores = Array(1) { FloatArray(MAX_DETECTIONS) }
        val numDetections = FloatArray(1)

        val outputMap = HashMap<Int, Any>()
        outputMap[0] = boxes
        outputMap[1] = classes
        outputMap[2] = scores
        outputMap[3] = numDetections

        interpreter!!.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

        // Filter for device classes above confidence threshold
        val count = numDetections[0].toInt().coerceAtMost(MAX_DETECTIONS)
        var bestConf = 0f
        var bestClass: String? = null
        var bestOverlap = false

        for (i in 0 until count) {
            val classId = classes[0][i].toInt()
            val conf = scores[0][i]

            if (conf < MIN_CONFIDENCE) continue
            if (classId !in DEVICE_CLASSES) continue

            val className = DEVICE_CLASS_NAMES[classId] ?: "device"

            // Box format: [ymin, xmin, ymax, xmax] normalized 0..1
            val ymin = boxes[0][i][0]
            val xmin = boxes[0][i][1]
            val ymax = boxes[0][i][2]
            val xmax = boxes[0][i][3]

            val overlap = bboxOverlap(
                xmin, ymin, xmax, ymax,
                faceBbox.left, faceBbox.top, faceBbox.right, faceBbox.bottom
            )

            if (conf > bestConf) {
                bestConf = conf
                bestClass = className
                bestOverlap = overlap > OVERLAP_THRESHOLD
            }
        }

        val deviceDetected = bestConf >= MIN_CONFIDENCE
        // Spoof score: high confidence + face overlap = very suspicious
        val spoofScore = if (deviceDetected) {
            if (bestOverlap) {
                // Device overlaps face — strong replay attack signal
                (bestConf * 1.2f).coerceAtMost(1f)
            } else {
                // Device in frame but not overlapping face — moderate signal
                bestConf * 0.5f
            }
        } else {
            0f
        }

        return DeviceDetectionResult(
            deviceDetected = deviceDetected,
            maxConfidence = bestConf,
            deviceClass = bestClass,
            overlapWithFace = bestOverlap,
            spoofScore = spoofScore
        )
    }

    override fun close() {
        interpreter?.close()
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            inputBuffer.put((pixel shr 16 and 0xFF).toByte()) // R
            inputBuffer.put((pixel shr 8 and 0xFF).toByte())  // G
            inputBuffer.put((pixel and 0xFF).toByte())          // B
        }
        inputBuffer.rewind()
    }

    /** Compute IoU (intersection over union) between two normalized bounding boxes. */
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
        val intersection = iw * ih

        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)
        val union = areaA + areaB - intersection

        return if (union > 0f) intersection / union else 0f
    }

    companion object {
        private const val MODEL_PATH = "models/device_detection.pad"
        private const val INPUT_SIZE = 300
        private const val MAX_DETECTIONS = 10
        private const val MIN_CONFIDENCE = 0.4f
        private const val OVERLAP_THRESHOLD = 0.05f

        // COCO class indices for devices (1-indexed in the labelmap)
        private val DEVICE_CLASSES = setOf(62, 63, 67)
        private val DEVICE_CLASS_NAMES = mapOf(
            62 to "tv",
            63 to "laptop",
            67 to "cell phone"
        )

        private val PLACEHOLDER_RESULT = DeviceDetectionResult(
            deviceDetected = false,
            maxConfidence = 0f,
            deviceClass = null,
            overlapWithFace = false,
            spoofScore = 0f
        )
    }
}
