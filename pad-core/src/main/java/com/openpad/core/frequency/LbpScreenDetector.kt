package com.openpad.core.frequency

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Screen display detector using macro-level image analysis.
 *
 * Detects screen-displayed faces via three signals that work regardless of
 * screen PPI (unlike moire-based detection):
 *
 * 1. **Color banding**: Screens quantize smooth gradients to 8-bit values,
 *    creating step artifacts in smooth skin regions. Real skin has continuous
 *    gradient variation from natural lighting.
 *
 * 2. **Focus uniformity**: Real faces have natural depth-of-field — parts of
 *    the face are sharper than others. Screen faces are uniformly in focus
 *    (everything is at the same focal plane). Measured via Laplacian variance
 *    across face quadrants.
 *
 * 3. **Color distribution**: Screens produce a narrower chrominance distribution
 *    with higher saturation uniformity. Real skin under ambient light shows
 *    more varied hue/saturation due to subsurface scattering and lighting.
 *
 * ~2-3ms per frame on modern ARM.
 */
class LbpScreenDetector {

    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): LbpResult {
        val crop = cropFace(bitmap, faceBbox)

        val bandingScore = detectColorBanding(crop)
        val focusUniformityScore = detectFocusUniformity(crop)
        val colorDistScore = analyzeColorDistribution(crop)

        val screenScore = (
            bandingScore * WEIGHT_BANDING +
                focusUniformityScore * WEIGHT_FOCUS +
                colorDistScore * WEIGHT_COLOR
            ).coerceIn(0f, 1f)

        return LbpResult(
            screenScore = screenScore,
            uniformity = focusUniformityScore,
            entropy = bandingScore,
            channelCorrelation = colorDistScore
        )
    }

    // ---- Signal 1: Color banding detection ----

    /**
     * Detect 8-bit color quantization banding in smooth face regions.
     *
     * Screen-displayed faces show uniform step-like transitions in smooth
     * gradient areas (cheeks, forehead) where adjacent pixels jump by exactly
     * 1 intensity level in a regular pattern. Real faces under natural light
     * have more varied gradients from camera sensor noise.
     *
     * Method:
     * 1. Identify smooth gradient regions (low magnitude)
     * 2. Count pixels where the gradient is exactly 1 in BOTH directions
     *    (simultaneous uniform step = strong screen indicator)
     * 3. Also measure the ratio of zero-gradient (perfectly flat) pixels
     *    (screens digitize more flat regions than real cameras with sensor noise)
     *
     * @return Score [0..1]. Higher = more banding = more likely screen.
     */
    private fun detectColorBanding(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        // Work in grayscale, keep full 8-bit precision
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }

        var smoothPixels = 0
        var flatPixels = 0       // gradient == 0 in both directions
        var uniformStepPixels = 0 // gradient == exactly 1 in both h and v

        for (y in 1 until CROP_SIZE - 1) {
            for (x in 1 until CROP_SIZE - 1) {
                val idx = y * CROP_SIZE + x
                val gx = abs(gray[idx + 1] - gray[idx - 1])
                val gy = abs(gray[idx + CROP_SIZE] - gray[idx - CROP_SIZE])

                if (gx <= SMOOTH_GRADIENT_THRESHOLD && gy <= SMOOTH_GRADIENT_THRESHOLD) {
                    smoothPixels++

                    val hGrad = abs(gray[idx + 1] - gray[idx])
                    val vGrad = abs(gray[idx + CROP_SIZE] - gray[idx])

                    if (hGrad == 0 && vGrad == 0) {
                        flatPixels++
                    }
                    // Uniform step: exactly 1 in both directions simultaneously
                    // This is rare in real cameras but common on screens
                    if (hGrad == 1 && vGrad == 1) {
                        uniformStepPixels++
                    }
                }
            }
        }

        if (smoothPixels < MIN_SMOOTH_PIXELS) return 0f

        // Flat ratio: screens produce more perfectly-flat patches
        val flatRatio = flatPixels.toFloat() / smoothPixels
        // Uniform step ratio: simultaneous unit steps are a strong screen signal
        val uniformStepRatio = uniformStepPixels.toFloat() / smoothPixels

        // Real camera: flatRatio ~0.05-0.20, uniformStepRatio ~0.02-0.08
        // Screen: flatRatio ~0.25-0.60, uniformStepRatio ~0.10-0.25
        val flatScore = ((flatRatio - 0.20f) / 0.30f).coerceIn(0f, 1f)
        val stepScore = ((uniformStepRatio - 0.08f) / 0.12f).coerceIn(0f, 1f)

        return (flatScore * 0.5f + stepScore * 0.5f)
    }

    // ---- Signal 2: Focus uniformity ----

    /**
     * Detect uniform focus (screen indicator).
     *
     * Real faces have depth-of-field: nose tip is sharper than ears, forehead
     * has different focus than chin. Screens display everything at the same
     * focal plane, so all regions are equally sharp.
     *
     * Method: Compute Laplacian variance in 4 quadrants. Low variance across
     * quadrants = uniform focus = screen. High variance = natural DOF = real.
     *
     * @return Score [0..1]. Higher = more uniform focus = more likely screen.
     */
    private fun detectFocusUniformity(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        val gray = FloatArray(pixels.size) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
        }

        // Compute Laplacian (edge detector)
        val sz = CROP_SIZE
        val laplacian = FloatArray(sz * sz)
        for (y in 1 until sz - 1) {
            for (x in 1 until sz - 1) {
                val idx = y * sz + x
                laplacian[idx] = -4f * gray[idx] +
                    gray[idx - 1] + gray[idx + 1] +
                    gray[idx - sz] + gray[idx + sz]
            }
        }

        // Compute sharpness (Laplacian variance) per quadrant
        val half = sz / 2
        val q1 = regionVariance(laplacian, sz, 2, half - 1, 2, half - 1)
        val q2 = regionVariance(laplacian, sz, 2, half - 1, half, sz - 2)
        val q3 = regionVariance(laplacian, sz, half, sz - 2, 2, half - 1)
        val q4 = regionVariance(laplacian, sz, half, sz - 2, half, sz - 2)

        val quadrants = floatArrayOf(q1, q2, q3, q4)
        val mean = quadrants.average().toFloat()

        if (mean < 1f) return 0.5f // Too dark/blurry to analyze

        // Coefficient of variation: std / mean
        val std = sqrt(quadrants.map { (it - mean) * (it - mean) }.average().toFloat())
        val cv = std / mean

        // Real faces: CV ~0.3-0.8 (significant DOF variation)
        // Screens: CV ~0.05-0.2 (uniform focus)
        // Invert: low CV = high screen likelihood
        val normalized = (1f - (cv / 0.5f)).coerceIn(0f, 1f)
        return normalized
    }

    // ---- Signal 3: Color distribution analysis ----

    /**
     * Analyze chrominance distribution for screen detection.
     *
     * Screens display skin tones with a more compressed, saturated color
     * distribution compared to real skin under ambient lighting. Real skin
     * shows broader hue/saturation spread due to subsurface scattering,
     * shadows, and varied ambient light reflection.
     *
     * Method: Convert to YCbCr-like space, compute statistics of Cb and Cr
     * channels. Screens show tighter clustering (lower std dev).
     *
     * @return Score [0..1]. Higher = more compressed distribution = more likely screen.
     */
    private fun analyzeColorDistribution(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        // Compute Cb and Cr (simplified YCbCr)
        val cbValues = FloatArray(pixels.size)
        val crValues = FloatArray(pixels.size)
        var skinPixels = 0

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16 and 0xFF).toFloat()
            val g = (c shr 8 and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()

            // Y = 0.299R + 0.587G + 0.114B
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            // Cb = 128 + (-0.169R - 0.331G + 0.500B)
            val cb = 128f + (-0.169f * r - 0.331f * g + 0.500f * b)
            // Cr = 128 + (0.500R - 0.419G - 0.081B)
            val cr = 128f + (0.500f * r - 0.419f * g - 0.081f * b)

            // Only analyze skin-tone pixels (rough YCbCr skin range)
            if (y in 40f..250f && cb in 77f..127f && cr in 133f..173f) {
                cbValues[skinPixels] = cb
                crValues[skinPixels] = cr
                skinPixels++
            }
        }

        if (skinPixels < MIN_SKIN_PIXELS) return 0.5f // Not enough skin

        // Compute std dev of Cb and Cr within skin region
        val cbStd = stdDev(cbValues, skinPixels)
        val crStd = stdDev(crValues, skinPixels)
        val avgStd = (cbStd + crStd) / 2f

        // Real skin: avgStd ~5-12 (broad chrominance spread)
        // Screens: avgStd ~2-5 (compressed distribution)
        // Lower std = more likely screen
        val normalized = (1f - (avgStd - 2f) / 8f).coerceIn(0f, 1f)
        return normalized
    }

    // ---- Helpers ----

    private fun regionVariance(
        data: FloatArray, stride: Int,
        yStart: Int, yEnd: Int, xStart: Int, xEnd: Int
    ): Float {
        var sum = 0f
        var sumSq = 0f
        var count = 0
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val v = data[y * stride + x]
                sum += v
                sumSq += v * v
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    private fun stdDev(values: FloatArray, count: Int): Float {
        if (count < 2) return 0f
        var sum = 0f
        var sumSq = 0f
        for (i in 0 until count) {
            sum += values[i]
            sumSq += values[i] * values[i]
        }
        val mean = sum / count
        return sqrt((sumSq / count - mean * mean).coerceAtLeast(0f))
    }

    private fun cropFace(bitmap: Bitmap, faceBbox: FaceDetection.BBox): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val left = (faceBbox.left * w).toInt().coerceIn(0, w - 1)
        val top = (faceBbox.top * h).toInt().coerceIn(0, h - 1)
        val right = (faceBbox.right * w).toInt().coerceIn(left + 1, w)
        val bottom = (faceBbox.bottom * h).toInt().coerceIn(top + 1, h)

        val cropW = right - left
        val cropH = bottom - top
        if (cropW < 2 || cropH < 2) {
            return Bitmap.createBitmap(CROP_SIZE, CROP_SIZE, Bitmap.Config.ARGB_8888)
        }

        val faceCrop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        return Bitmap.createScaledBitmap(faceCrop, CROP_SIZE, CROP_SIZE, true)
    }

    companion object {
        private const val CROP_SIZE = 64

        // Banding detection
        private const val SMOOTH_GRADIENT_THRESHOLD = 8
        private const val MIN_SMOOTH_PIXELS = 100

        // Color distribution
        private const val MIN_SKIN_PIXELS = 200

        // Signal fusion weights
        private const val WEIGHT_BANDING = 0.40f
        private const val WEIGHT_FOCUS = 0.30f
        private const val WEIGHT_COLOR = 0.30f
    }
}
