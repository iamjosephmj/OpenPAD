package com.openpad.core.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.openpad.core.detection.FaceDetection
import com.openpad.core.model.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ESPCN x2 super-resolution enhancer for the face region.
 *
 * Processes only the Y (luminance) channel at 128x128 input, producing a
 * 256x256 enhanced output. Cb/Cr channels are bilinearly upscaled and
 * recombined to produce the final RGB result.
 *
 * Follows the same pattern as [com.openpad.core.texture.MiniFasNetAnalyzer]:
 * constructor with [ModelLoader], [isPlaceholder] flag, reusable [ByteBuffer],
 * and [close].
 */
class EspcnFrameEnhancer(context: Context) : FrameEnhancer {

    private val interpreter: Interpreter?
    private val isPlaceholder: Boolean

    /** Reusable input buffer: [1, 128, 128, 1] float32 = 65,536 bytes. */
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    /** Reusable output buffer: [1, 256, 256, 1] float32 = 262,144 bytes. */
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(OUTPUT_SIZE * OUTPUT_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    init {
        val appContext = context.applicationContext
        val opts = ModelLoader.createOptions(threads = 2, useGpu = false)
        interpreter = try {
            val model = ModelLoader.loadFromAssets(appContext, MODEL_PATH)
            Interpreter(model, opts)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
        isPlaceholder = interpreter == null
    }

    override fun enhance(bitmap: Bitmap, faceBbox: FaceDetection.BBox): Bitmap? {
        if (isPlaceholder) return null

        val imgW = bitmap.width
        val imgH = bitmap.height

        // Crop face region with 1.5x margin
        val faceCX = (faceBbox.left + faceBbox.right) / 2f * imgW
        val faceCY = (faceBbox.top + faceBbox.bottom) / 2f * imgH
        val faceW = faceBbox.width() * imgW * CROP_MARGIN
        val faceH = faceBbox.height() * imgH * CROP_MARGIN

        val cropLeft = (faceCX - faceW / 2f).toInt().coerceIn(0, imgW - 1)
        val cropTop = (faceCY - faceH / 2f).toInt().coerceIn(0, imgH - 1)
        val cropRight = (faceCX + faceW / 2f).toInt().coerceIn(cropLeft + 1, imgW)
        val cropBottom = (faceCY + faceH / 2f).toInt().coerceIn(cropTop + 1, imgH)

        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW < 4 || cropH < 4) return null

        val faceCrop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(faceCrop, INPUT_SIZE, INPUT_SIZE, true)
        if (scaled !== faceCrop) faceCrop.recycle()

        // Extract Y, Cb, Cr from RGB
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        scaled.recycle()

        val yChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val cbChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val crChannel = FloatArray(INPUT_SIZE * INPUT_SIZE)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16 and 0xFF).toFloat()
            val g = (c shr 8 and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()

            yChannel[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            cbChannel[i] = (-0.169f * r - 0.331f * g + 0.500f * b + 128f) / 255f
            crChannel[i] = (0.500f * r - 0.419f * g - 0.081f * b + 128f) / 255f
        }

        // Fill input buffer with Y channel
        inputBuffer.rewind()
        for (v in yChannel) {
            inputBuffer.putFloat(v)
        }
        inputBuffer.rewind()

        // Run ESPCN inference
        outputBuffer.rewind()
        interpreter!!.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        // Read enhanced Y channel (256x256)
        val enhancedY = FloatArray(OUTPUT_SIZE * OUTPUT_SIZE)
        for (i in enhancedY.indices) {
            enhancedY[i] = outputBuffer.getFloat()
        }

        // Quality gate: compare center-patch sharpness of input vs enhanced Y
        val inputCenterVar = centerPatchVariance(yChannel, INPUT_SIZE)
        val enhancedCenterVar = centerPatchVariance(enhancedY, OUTPUT_SIZE)
        // Enhanced patch should be meaningfully sharper; if not, SR didn't help
        if (enhancedCenterVar <= inputCenterVar * QUALITY_GATE_MIN_IMPROVEMENT) return null

        // Bilinear upscale Cb/Cr from 128x128 to 256x256
        val upCb = bilinearUpscale2x(cbChannel, INPUT_SIZE)
        val upCr = bilinearUpscale2x(crChannel, INPUT_SIZE)

        // Reconstruct RGB from enhanced Y + upscaled CbCr
        val outPixels = IntArray(OUTPUT_SIZE * OUTPUT_SIZE)
        for (i in outPixels.indices) {
            val y = enhancedY[i].coerceIn(0f, 1f) * 255f
            val cb = upCb[i] * 255f - 128f
            val cr = upCr[i] * 255f - 128f

            val r = (y + 1.402f * cr).toInt().coerceIn(0, 255)
            val g = (y - 0.344f * cb - 0.714f * cr).toInt().coerceIn(0, 255)
            val b = (y + 1.772f * cb).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val enhancedFace = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        enhancedFace.setPixels(outPixels, 0, OUTPUT_SIZE, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)

        // Scale enhanced face back to original crop size
        val restoredFace = Bitmap.createScaledBitmap(enhancedFace, cropW, cropH, true)
        if (restoredFace !== enhancedFace) enhancedFace.recycle()

        // Composite enhanced face back into a copy of the original bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val blendPaint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmap(
            restoredFace,
            null,
            Rect(cropLeft, cropTop, cropRight, cropBottom),
            blendPaint
        )
        restoredFace.recycle()

        return result
    }

    override fun close() {
        interpreter?.close()
    }

    /**
     * Compute Laplacian variance over a 16x16 center patch of a [size]x[size]
     * float array (Y channel, [0,1] normalized). Used as a quick quality check.
     */
    private fun centerPatchVariance(data: FloatArray, size: Int): Float {
        val patchSize = QUALITY_PATCH_SIZE.coerceAtMost(size - 2)
        val startX = (size - patchSize) / 2
        val startY = (size - patchSize) / 2

        var sum = 0f
        var sumSq = 0f
        var count = 0
        for (y in startY + 1 until startY + patchSize - 1) {
            for (x in startX + 1 until startX + patchSize - 1) {
                val idx = y * size + x
                val lap = -4f * data[idx] +
                    data[idx - 1] + data[idx + 1] +
                    data[idx - size] + data[idx + size]
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    /**
     * Simple bilinear 2x upscale for a [size]x[size] float array.
     * Returns a [size*2]x[size*2] float array.
     */
    private fun bilinearUpscale2x(input: FloatArray, size: Int): FloatArray {
        val outSize = size * 2
        val output = FloatArray(outSize * outSize)

        for (oy in 0 until outSize) {
            for (ox in 0 until outSize) {
                val srcX = (ox * (size - 1).toFloat()) / (outSize - 1).toFloat()
                val srcY = (oy * (size - 1).toFloat()) / (outSize - 1).toFloat()

                val x0 = srcX.toInt().coerceIn(0, size - 2)
                val y0 = srcY.toInt().coerceIn(0, size - 2)
                val x1 = x0 + 1
                val y1 = y0 + 1

                val fx = srcX - x0
                val fy = srcY - y0

                val v00 = input[y0 * size + x0]
                val v10 = input[y0 * size + x1]
                val v01 = input[y1 * size + x0]
                val v11 = input[y1 * size + x1]

                output[oy * outSize + ox] =
                    v00 * (1f - fx) * (1f - fy) +
                    v10 * fx * (1f - fy) +
                    v01 * (1f - fx) * fy +
                    v11 * fx * fy
            }
        }
        return output
    }

    companion object {
        private const val INPUT_SIZE = 128
        private const val OUTPUT_SIZE = 256
        private const val CROP_MARGIN = 1.5f
        private const val MODEL_PATH = "models/face_enhance.pad"
        /** Side length of center patch for quality gate Laplacian check. */
        private const val QUALITY_PATCH_SIZE = 16
        /** Enhanced sharpness must exceed input by this factor, otherwise discard. */
        private const val QUALITY_GATE_MIN_IMPROVEMENT = 1.1f
    }
}
