package com.openpad.core.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts CameraX [ImageProxy] (YUV_420_888) to [Bitmap].
 * Ported from PadAnalyzer.imageToBitmap.
 */
object BitmapConverter {

    private const val JPEG_QUALITY = 90

    fun imageToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), JPEG_QUALITY, out)
            val jpegBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
                }
            }
        }

        val uvRowStride = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2

        if (uvPixelStride == 2 && uvRowStride == width) {
            vBuffer.position(0)
            vBuffer.get(nv21, ySize, uvSize.coerceAtMost(vBuffer.remaining()))
        } else {
            var uvPos = ySize
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vIdx = row * uvRowStride + col * uvPixelStride
                    val uIdx = row * uPlane.rowStride + col * uPlane.pixelStride
                    nv21[uvPos++] = vBuffer.get(vIdx)
                    nv21[uvPos++] = uBuffer.get(uIdx)
                }
            }
        }

        return nv21
    }

    /** Downsample bitmap to small grayscale float array for frame similarity. */
    fun downsampleToGray(bitmap: Bitmap, size: Int = SIMILARITY_SIZE): FloatArray {
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        return FloatArray(pixels.size) { i ->
            val c = pixels[i]
            ((c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
        }
    }

    /** Compute similarity between two grayscale frames as 1 - MAD. */
    fun computeFrameSimilarity(prev: FloatArray?, curr: FloatArray): Float {
        if (prev == null || prev.size != curr.size) return 0f
        var sumDiff = 0f
        for (i in prev.indices) {
            val d = prev[i] - curr[i]
            sumDiff += if (d < 0) -d else d
        }
        return 1f - sumDiff / prev.size
    }

    /** Compute face sharpness via Laplacian variance + DOF quadrant probe. */
    fun computeFaceSharpness(
        bitmap: Bitmap,
        left: Float, top: Float, right: Float, bottom: Float
    ): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val l = (left * w).toInt().coerceIn(0, w - 1)
        val t = (top * h).toInt().coerceIn(0, h - 1)
        val r = (right * w).toInt().coerceIn(l + 1, w)
        val b = (bottom * h).toInt().coerceIn(t + 1, h)

        val cropW = r - l
        val cropH = b - t
        if (cropW < 4 || cropH < 4) return Pair(0f, 0f)

        val faceCrop = Bitmap.createBitmap(bitmap, l, t, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(faceCrop, SHARPNESS_SIZE, SHARPNESS_SIZE, true)

        val pixels = IntArray(SHARPNESS_SIZE * SHARPNESS_SIZE)
        scaled.getPixels(pixels, 0, SHARPNESS_SIZE, 0, 0, SHARPNESS_SIZE, SHARPNESS_SIZE)
        val gray = FloatArray(pixels.size) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
        }

        val sz = SHARPNESS_SIZE
        val laplacian = FloatArray(sz * sz)
        for (y in 1 until sz - 1) {
            for (x in 1 until sz - 1) {
                val idx = y * sz + x
                laplacian[idx] = -4f * gray[idx] +
                    gray[idx - 1] + gray[idx + 1] +
                    gray[idx - sz] + gray[idx + sz]
            }
        }

        val overall = laplacianVariance(laplacian, sz, 1, sz - 1, 1, sz - 1)

        val half = sz / 2
        val qTL = laplacianVariance(laplacian, sz, 1, half, 1, half)
        val qTR = laplacianVariance(laplacian, sz, 1, half, half, sz - 1)
        val qBL = laplacianVariance(laplacian, sz, half, sz - 1, 1, half)
        val qBR = laplacianVariance(laplacian, sz, half, sz - 1, half, sz - 1)

        val quadrants = floatArrayOf(qTL, qTR, qBL, qBR)
        val qMean = quadrants.average().toFloat()
        val quadrantVariance = quadrants.map { (it - qMean) * (it - qMean) }.average().toFloat()

        return Pair(overall.coerceAtLeast(0f), quadrantVariance.coerceAtLeast(0f))
    }

    private fun laplacianVariance(
        laplacian: FloatArray, stride: Int,
        yStart: Int, yEnd: Int, xStart: Int, xEnd: Int
    ): Float {
        var sum = 0f
        var sumSq = 0f
        var count = 0
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val v = laplacian[y * stride + x]
                sum += v
                sumSq += v * v
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    private const val SIMILARITY_SIZE = 32
    private const val SHARPNESS_SIZE = 64

    /** Crop face to 64x64 grayscale float array for native FFT. Values in [0,1]. */
    fun cropFaceTo64Gray(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): FloatArray {
        val crop = cropFaceToSize(bitmap, bbox, 64, 1.0f)
        val pixels = IntArray(64 * 64)
        crop.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        return FloatArray(64 * 64) { i ->
            val c = pixels[i]
            ((c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
        }
    }

    /** Crop face to 64x64 ARGB bytes for native LBP. Order: A,R,G,B per pixel. */
    fun cropFaceTo64Argb(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): ByteArray {
        val crop = cropFaceToSize(bitmap, bbox, 64, 1.0f)
        val pixels = IntArray(64 * 64)
        crop.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        val out = ByteArray(64 * 64 * 4)
        for (i in pixels.indices) {
            val c = pixels[i]
            out[i * 4] = (c shr 24).toByte()
            out[i * 4 + 1] = (c shr 16).toByte()
            out[i * 4 + 2] = (c shr 8).toByte()
            out[i * 4 + 3] = (c and 0xFF).toByte()
        }
        return out
    }

    /** Crop face to 80x80 ARGB bytes for native photometric. Order: A,R,G,B per pixel. */
    fun cropFaceTo80Argb(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): ByteArray {
        val crop = cropFaceToSize(bitmap, bbox, 80, 1.0f)
        val pixels = IntArray(80 * 80)
        crop.getPixels(pixels, 0, 80, 0, 0, 80, 80)
        val out = ByteArray(80 * 80 * 4)
        for (i in pixels.indices) {
            val c = pixels[i]
            out[i * 4] = (c shr 24).toByte()
            out[i * 4 + 1] = (c shr 16).toByte()
            out[i * 4 + 2] = (c shr 8).toByte()
            out[i * 4 + 3] = (c and 0xFF).toByte()
        }
        return out
    }

    private fun cropFaceToSize(
        bitmap: Bitmap,
        bbox: com.openpad.core.detection.FaceDetection.BBox,
        targetSize: Int,
        margin: Float
    ): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = (bbox.left + bbox.right) / 2f * imgW
        val faceCY = (bbox.top + bbox.bottom) / 2f * imgH
        val faceW = bbox.width() * imgW * margin
        val faceH = bbox.height() * imgH * margin

        val left = (faceCX - faceW / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - faceH / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + faceW / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + faceH / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }
}
