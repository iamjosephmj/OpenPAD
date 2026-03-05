package com.openpad.core.analyzer

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Converts CameraX [ImageProxy] (YUV_420_888) to [Bitmap].
 *
 * Uses direct BT.601 YUV→RGB conversion instead of the lossy
 * YUV→NV21→JPEG→Bitmap path to preserve full color fidelity.
 */
object BitmapConverter {

    fun imageToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val width = image.width
            val height = image.height
            val pixels = yuv420ToArgbPixels(image, width, height)
            val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
    }

    /**
     * Direct YUV_420_888 → ARGB pixel conversion using BT.601 coefficients.
     * Avoids lossy JPEG intermediate that drops ~10% of color information.
     */
    private fun yuv420ToArgbPixels(image: ImageProxy, width: Int, height: Int): IntArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val pixels = IntArray(width * height)

        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val uvRowOffset = (row shr 1) * uvRowStride
            for (col in 0 until width) {
                val y = (yBuf.get(yRowOffset + col * yPixelStride).toInt() and 0xFF)
                val uvCol = (col shr 1) * uvPixelStride
                val u = (uBuf.get(uvRowOffset + uvCol).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvRowOffset + uvCol).toInt() and 0xFF) - 128

                var r = y + ((V_TO_R * v + ROUNDING) shr PRECISION)
                var g = y - ((U_TO_G * u + V_TO_G * v + ROUNDING) shr PRECISION)
                var b = y + ((U_TO_B * u + ROUNDING) shr PRECISION)

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    // BT.601 fixed-point coefficients (precision = 10 bits)
    private const val PRECISION = 10
    private const val ROUNDING = 1 shl (PRECISION - 1)
    private const val V_TO_R = 1436   // 1.402 * 1024
    private const val U_TO_G = 352    // 0.344 * 1024
    private const val V_TO_G = 731    // 0.714 * 1024
    private const val U_TO_B = 1815   // 1.772 * 1024

    /** Downsample bitmap to small grayscale float array for frame similarity. */
    fun downsampleToGray(bitmap: Bitmap, size: Int = SIMILARITY_SIZE): FloatArray {
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small !== bitmap) small.recycle()
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
        if (scaled !== faceCrop && faceCrop !== bitmap) faceCrop.recycle()

        val pixels = IntArray(SHARPNESS_SIZE * SHARPNESS_SIZE)
        scaled.getPixels(pixels, 0, SHARPNESS_SIZE, 0, 0, SHARPNESS_SIZE, SHARPNESS_SIZE)
        scaled.recycle()
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

    /** Average luminance of the face region, normalized to [0, 1]. */
    fun computeFaceLuminance(
        bitmap: Bitmap,
        bbox: com.openpad.core.detection.FaceDetection.BBox
    ): Float {
        val w = bitmap.width
        val h = bitmap.height
        val l = (bbox.left * w).toInt().coerceIn(0, w - 1)
        val t = (bbox.top * h).toInt().coerceIn(0, h - 1)
        val r = (bbox.right * w).toInt().coerceIn(l + 1, w)
        val b = (bbox.bottom * h).toInt().coerceIn(t + 1, h)

        val cropW = r - l
        val cropH = b - t
        if (cropW < 2 || cropH < 2) return 0.5f

        val crop = Bitmap.createBitmap(bitmap, l, t, cropW, cropH)
        val sample = Bitmap.createScaledBitmap(crop, LUMINANCE_SAMPLE_SIZE, LUMINANCE_SAMPLE_SIZE, true)
        if (sample !== crop) crop.recycle()

        val pixels = IntArray(LUMINANCE_SAMPLE_SIZE * LUMINANCE_SAMPLE_SIZE)
        sample.getPixels(pixels, 0, LUMINANCE_SAMPLE_SIZE, 0, 0, LUMINANCE_SAMPLE_SIZE, LUMINANCE_SAMPLE_SIZE)
        sample.recycle()

        var sum = 0f
        for (c in pixels) {
            sum += ((c shr 16 and 0xFF) * 0.299f +
                (c shr 8 and 0xFF) * 0.587f +
                (c and 0xFF) * 0.114f) / 255f
        }
        return sum / pixels.size
    }

    private const val LUMINANCE_SAMPLE_SIZE = 16
    private const val SIMILARITY_SIZE = 32
    private const val SHARPNESS_SIZE = 64
    private const val FACE_CROP_SIZE = 112
    private const val DISPLAY_CROP_SIZE = 224
    private const val CROP_MARGIN = 1.3f

    /**
     * Crop and scale face to a square [FACE_CROP_SIZE]x[FACE_CROP_SIZE] bitmap.
     * The face is expanded by [CROP_MARGIN] to include some context.
     */
    fun cropFace(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = (bbox.left + bbox.right) / 2f * imgW
        val faceCY = (bbox.top + bbox.bottom) / 2f * imgH
        val faceW = bbox.width() * imgW * CROP_MARGIN
        val faceH = bbox.height() * imgH * CROP_MARGIN
        val side = maxOf(faceW, faceH)

        val left = (faceCX - side / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - side / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + side / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + side / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val scaled = Bitmap.createScaledBitmap(cropped, FACE_CROP_SIZE, FACE_CROP_SIZE, true)
        if (scaled !== cropped && cropped !== bitmap) cropped.recycle()
        return scaled
    }

    /**
     * Crop a fixed-pixel region around the face center from the raw camera frame.
     * Unlike [cropFace], this does NOT normalize the face to fill the output,
     * so a closer face appears proportionally larger.
     */
    fun cropFaceForDisplay(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = ((bbox.left + bbox.right) / 2f * imgW).toInt()
        val faceCY = ((bbox.top + bbox.bottom) / 2f * imgH).toInt()
        val half = DISPLAY_CROP_SIZE / 2

        val left = (faceCX - half).coerceIn(0, imgW - 1)
        val top = (faceCY - half).coerceIn(0, imgH - 1)
        val right = (faceCX + half).coerceIn(left + 1, imgW)
        val bottom = (faceCY + half).coerceIn(top + 1, imgH)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Crop face to 64x64 grayscale float array for native FFT. Values in [0,1]. */
    fun cropFaceTo64Gray(bitmap: Bitmap, bbox: com.openpad.core.detection.FaceDetection.BBox): FloatArray {
        val crop = cropFaceToSize(bitmap, bbox, 64, 1.0f)
        val pixels = IntArray(64 * 64)
        crop.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        crop.recycle()
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
        crop.recycle()
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
        crop.recycle()
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
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (scaled !== cropped && cropped !== bitmap) cropped.recycle()
        return scaled
    }
}
