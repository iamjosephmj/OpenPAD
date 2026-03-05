package com.openpad.core.analyzer

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Classical image preprocessing applied to camera frames before ML inference.
 *
 * Two stages are applied in order:
 * 1. **Adaptive gamma correction** — normalises brightness based on measured
 *    face luminance. Uses a 256-entry LUT so cost is O(pixels).
 * 2. **CLAHE** (Contrast Limited Adaptive Histogram Equalization) — enhances
 *    local contrast on the luminance channel while preserving colour.
 *    Especially effective in low-light and uneven lighting.
 *
 * Both stages are independently disableable via config parameters.
 */
internal class FramePreprocessor(
    private val gammaTarget: Float,
    private val claheClipLimit: Float,
    private val claheTileGridSize: Int = DEFAULT_TILE_GRID
) {

    /**
     * @param bitmap   source bitmap (not recycled by this method)
     * @param luminance measured face luminance in [0, 1]
     * @return new preprocessed [Bitmap]; caller must recycle
     */
    fun preprocess(bitmap: Bitmap, luminance: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (gammaTarget > 0f) {
            applyAdaptiveGamma(pixels, luminance)
        }

        if (claheClipLimit > 0f) {
            applyClahe(pixels, width, height)
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    // ── Adaptive Gamma ──────────────────────────────────────────────────

    /**
     * Compute gamma from measured luminance vs target and apply via LUT.
     * If luminance is already close to [gammaTarget], this is a near-identity.
     */
    private fun applyAdaptiveGamma(pixels: IntArray, luminance: Float) {
        val clamped = luminance.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)
        val gamma = ln(gammaTarget.toDouble()) / ln(clamped.toDouble())
        val clampedGamma = gamma.coerceIn(MIN_GAMMA, MAX_GAMMA)

        if (clampedGamma in IDENTITY_RANGE) return

        val lut = IntArray(256) { i ->
            (255.0 * (i / 255.0).pow(clampedGamma)).roundToInt().coerceIn(0, 255)
        }

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = c and ALPHA_MASK.toInt()
            val r = lut[(c shr 16) and 0xFF]
            val g = lut[(c shr 8) and 0xFF]
            val b = lut[c and 0xFF]
            pixels[i] = a or (r shl 16) or (g shl 8) or b
        }
    }

    // ── CLAHE ───────────────────────────────────────────────────────────

    /**
     * Contrast Limited Adaptive Histogram Equalization on the luminance channel.
     *
     * Steps:
     * 1. Convert each pixel's RGB to a luminance value [0, 255].
     * 2. Divide the image into tiles and compute a clipped histogram per tile.
     * 3. Build per-tile CDFs from the clipped histograms.
     * 4. For each pixel, bilinearly interpolate the mapped luminance from the
     *    four nearest tile CDFs.
     * 5. Scale the original RGB channels by the luminance ratio.
     */
    private fun applyClahe(pixels: IntArray, width: Int, height: Int) {
        val tilesX = claheTileGridSize
        val tilesY = claheTileGridSize
        val tileW = width / tilesX
        val tileH = height / tilesY
        if (tileW < 2 || tileH < 2) return

        val lum = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            lum[i] = luminanceFromPixel(c)
        }

        val cdfs = Array(tilesY) { ty ->
            Array(tilesX) { tx ->
                buildTileCdf(lum, width, height, tx, ty, tileW, tileH)
            }
        }

        for (y in 0 until height) {
            val fy = ((y - tileH / 2).toFloat() / tileH).coerceIn(0f, (tilesY - 1).toFloat())
            val ty0 = fy.toInt().coerceIn(0, tilesY - 1)
            val ty1 = (ty0 + 1).coerceIn(0, tilesY - 1)
            val wy = fy - ty0

            for (x in 0 until width) {
                val fx = ((x - tileW / 2).toFloat() / tileW).coerceIn(0f, (tilesX - 1).toFloat())
                val tx0 = fx.toInt().coerceIn(0, tilesX - 1)
                val tx1 = (tx0 + 1).coerceIn(0, tilesX - 1)
                val wx = fx - tx0

                val idx = y * width + x
                val l = lum[idx]

                val v00 = cdfs[ty0][tx0][l]
                val v01 = cdfs[ty0][tx1][l]
                val v10 = cdfs[ty1][tx0][l]
                val v11 = cdfs[ty1][tx1][l]

                val top = v00 * (1f - wx) + v01 * wx
                val bot = v10 * (1f - wx) + v11 * wx
                val mapped = ((top * (1f - wy) + bot * wy)).roundToInt().coerceIn(0, 255)

                val original = l.coerceAtLeast(1)
                val scale = mapped.toFloat() / original

                val c = pixels[idx]
                val a = c and ALPHA_MASK.toInt()
                val r = ((c shr 16 and 0xFF) * scale).roundToInt().coerceIn(0, 255)
                val g = ((c shr 8 and 0xFF) * scale).roundToInt().coerceIn(0, 255)
                val b = ((c and 0xFF) * scale).roundToInt().coerceIn(0, 255)
                pixels[idx] = a or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun buildTileCdf(
        lum: IntArray, imgW: Int, imgH: Int,
        tx: Int, ty: Int, tileW: Int, tileH: Int
    ): FloatArray {
        val x0 = tx * tileW
        val y0 = ty * tileH
        val x1 = (x0 + tileW).coerceAtMost(imgW)
        val y1 = (y0 + tileH).coerceAtMost(imgH)

        val hist = IntArray(HIST_BINS)
        var count = 0
        for (y in y0 until y1) {
            val rowOff = y * imgW
            for (x in x0 until x1) {
                hist[lum[rowOff + x]]++
                count++
            }
        }

        if (count == 0) return FloatArray(HIST_BINS) { it.toFloat() }

        val clipCount = (claheClipLimit * count / HIST_BINS).toInt().coerceAtLeast(1)
        var excess = 0
        for (i in 0 until HIST_BINS) {
            if (hist[i] > clipCount) {
                excess += hist[i] - clipCount
                hist[i] = clipCount
            }
        }
        val perBin = excess / HIST_BINS
        val remainder = excess - perBin * HIST_BINS
        for (i in 0 until HIST_BINS) {
            hist[i] += perBin
            if (i < remainder) hist[i]++
        }

        val cdf = FloatArray(HIST_BINS)
        var cumulative = 0
        for (i in 0 until HIST_BINS) {
            cumulative += hist[i]
            cdf[i] = (cumulative - 1).toFloat() / (count - 1).coerceAtLeast(1) * 255f
        }
        return cdf
    }

    companion object {
        private const val HIST_BINS = 256
        private const val DEFAULT_TILE_GRID = 8
        private const val MIN_LUMINANCE = 0.05f
        private const val MAX_LUMINANCE = 0.95f
        private const val MIN_GAMMA = 0.3
        private const val MAX_GAMMA = 3.0
        private val IDENTITY_RANGE = 0.95..1.05
        private const val ALPHA_MASK = 0xFF000000L

        internal fun luminanceFromPixel(argb: Int): Int {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
        }

        /**
         * Build a gamma LUT — exposed for unit testing.
         */
        internal fun buildGammaLut(luminance: Float, gammaTarget: Float): IntArray? {
            if (gammaTarget <= 0f || gammaTarget >= 1f) return null
            val clamped = luminance.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)
            val gamma = ln(gammaTarget.toDouble()) / ln(clamped.toDouble())
            val clampedGamma = gamma.coerceIn(MIN_GAMMA, MAX_GAMMA)
            if (clampedGamma in IDENTITY_RANGE) return null
            return IntArray(256) { i ->
                (255.0 * (i / 255.0).pow(clampedGamma)).roundToInt().coerceIn(0, 255)
            }
        }
    }
}
