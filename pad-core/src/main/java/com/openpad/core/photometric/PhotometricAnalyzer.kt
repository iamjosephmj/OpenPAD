package com.openpad.core.photometric

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import com.openpad.core.detection.FaceDetection
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Photometric analysis layer: detects spoofs via light and surface properties.
 *
 * Four independent signals, all pure image processing (no ML models):
 *
 * 1. **Specular highlight analysis**: Real faces have localized bright spots
 *    (nose tip, forehead, cheeks) from directional light. Screens have
 *    diffuse backlight glow. Prints have no specular at all.
 *
 * 2. **Chrominance spread**: Real skin under ambient light shows broad
 *    YCbCr distribution from subsurface scattering and varied lighting.
 *    Screens compress the color gamut.
 *
 * 3. **Edge DOF gradient**: Real faces at arm's length have natural
 *    depth-of-field — face boundary (ears, hairline) is softer than
 *    nose/eyes at the focal point. Screens are uniformly sharp.
 *
 * 4. **Lighting consistency**: Real faces have a dominant light direction
 *    creating asymmetric brightness (one side brighter). Screens self-illuminate,
 *    creating more symmetric or inconsistent lighting patterns.
 *
 * ~3-5ms per frame on modern ARM.
 */
class PhotometricAnalyzer {

    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): PhotometricResult {
        val crop = cropFace(bitmap, faceBbox, CROP_SIZE, 1.0f)
        val widerCrop = cropFace(bitmap, faceBbox, CROP_SIZE, 1.6f)

        try {
            val specularScore = analyzeSpecularHighlights(crop)
            val chrominanceScore = analyzeChrominanceSpread(crop)
            val edgeDofScore = analyzeEdgeDofGradient(crop, widerCrop)
            val lightingScore = analyzeLightingConsistency(crop)

            val combinedScore = (
                specularScore * WEIGHT_SPECULAR +
                    chrominanceScore * WEIGHT_CHROMINANCE +
                    edgeDofScore * WEIGHT_EDGE_DOF +
                    lightingScore * WEIGHT_LIGHTING
                ).coerceIn(0f, 1f)

            return PhotometricResult(
                specularScore = specularScore,
                chrominanceScore = chrominanceScore,
                edgeDofScore = edgeDofScore,
                lightingScore = lightingScore,
                combinedScore = combinedScore
            )
        } finally {
            crop.recycle()
            widerCrop.recycle()
        }
    }

    // ---- Signal 1: Specular Highlight Analysis ----

    /**
     * Detect natural specular highlight patterns.
     *
     * Real faces have small, bright, localized specular highlights on convex
     * surfaces (nose bridge, forehead, cheekbones) caused by directional light
     * reflecting off oily skin. These highlights are:
     * - Few in number (3-8 spots typically)
     * - High intensity (near white)
     * - Small relative to face area
     * - Located in specific face regions (upper face tends to be brighter)
     *
     * Screen-displayed faces have:
     * - Diffuse backlight glow (large bright areas, not localized)
     * - OR the original photo's highlights, but they won't match the current
     *   lighting environment
     *
     * Prints have:
     * - No specular highlights at all (matte/semi-matte paper)
     *
     * Method: Threshold for bright pixels, analyze their spatial distribution
     * (clustered small blobs = real, diffuse spread = screen, none = print).
     *
     * @return Score [0..1]. High = natural specular pattern = more likely real.
     */
    private fun analyzeSpecularHighlights(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        val totalPixels = CROP_SIZE * CROP_SIZE

        // Convert to luminance
        val lum = FloatArray(totalPixels) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
        }

        // Compute face mean luminance for adaptive thresholding
        val meanLum = lum.sum() / totalPixels
        val highlightThreshold = (meanLum + SPECULAR_OFFSET).coerceAtMost(245f)

        // Find highlight pixels
        var highlightCount = 0
        var highlightSumX = 0f
        var highlightSumY = 0f
        var highlightSumXX = 0f
        var highlightSumYY = 0f
        // Track if highlights are in upper half (expected for real faces due to forehead/nose)
        var upperHighlights = 0

        for (y in 0 until CROP_SIZE) {
            for (x in 0 until CROP_SIZE) {
                if (lum[y * CROP_SIZE + x] >= highlightThreshold) {
                    highlightCount++
                    highlightSumX += x
                    highlightSumY += y
                    highlightSumXX += x * x
                    highlightSumYY += y * y
                    if (y < CROP_SIZE / 2) upperHighlights++
                }
            }
        }

        val highlightRatio = highlightCount.toFloat() / totalPixels

        // No highlights at all → likely print (or very dark image)
        if (highlightCount < MIN_HIGHLIGHT_PIXELS) {
            return 0.3f // Slightly suspicious — could be poor lighting
        }

        // Too many highlights → diffuse glow = screen backlight
        if (highlightRatio > MAX_HIGHLIGHT_RATIO) {
            // Score decreases as highlight area grows beyond natural range
            val excess = (highlightRatio - MAX_HIGHLIGHT_RATIO) / 0.15f
            return (0.4f - excess * 0.3f).coerceIn(0.1f, 0.4f)
        }

        // Analyze spatial concentration (variance of highlight positions)
        // Clustered = real (a few bright spots), scattered = screen glow
        val meanX = highlightSumX / highlightCount
        val meanY = highlightSumY / highlightCount
        val varX = highlightSumXX / highlightCount - meanX * meanX
        val varY = highlightSumYY / highlightCount - meanY * meanY
        val spatialSpread = sqrt(varX + varY) / CROP_SIZE

        // Real face: concentrated highlights → low spread (~0.05-0.20)
        // Screen: scattered glow → high spread (~0.25-0.40)
        val concentrationScore = (1f - (spatialSpread / 0.35f)).coerceIn(0f, 1f)

        // Upper-face bias: real specular highlights tend to be on forehead/nose
        val upperRatio = upperHighlights.toFloat() / highlightCount
        val upperScore = if (upperRatio > 0.4f) 1f else upperRatio / 0.4f

        return (concentrationScore * 0.6f + upperScore * 0.2f + 0.2f).coerceIn(0f, 1f)
    }

    // ---- Signal 2: Chrominance Spread ----

    /**
     * Analyze skin chrominance distribution breadth.
     *
     * Real skin has broad chrominance variation due to:
     * - Subsurface scattering (blood vessels, melanin vary across face)
     * - Ambient light color variation (warm highlights, cool shadows)
     * - Oily vs dry skin regions
     *
     * Screens compress this into a narrower gamut due to:
     * - Display color space limitations (sRGB)
     * - Gamma encoding/decoding
     * - White balance applied at capture time
     *
     * Method: Compute 2D spread (covariance) of Cb/Cr values for skin pixels.
     * Larger spread ellipse = more natural = real.
     *
     * @return Score [0..1]. High = broad chrominance = more likely real.
     */
    private fun analyzeChrominanceSpread(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        var sumCb = 0f
        var sumCr = 0f
        var sumCbCb = 0f
        var sumCrCr = 0f
        var sumCbCr = 0f
        var skinCount = 0

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = 128f + (-0.169f * r - 0.331f * g + 0.500f * b)
            val cr = 128f + (0.500f * r - 0.419f * g - 0.081f * b)

            // Universal skin-tone filter (Fitzpatrick I–VI)
            if (isSkin(y, cb, cr)) {
                sumCb += cb
                sumCr += cr
                sumCbCb += cb * cb
                sumCrCr += cr * cr
                sumCbCr += cb * cr
                skinCount++
            }
        }

        if (skinCount < MIN_SKIN_PIXELS) return 0.5f

        val meanCb = sumCb / skinCount
        val meanCr = sumCr / skinCount
        val varCb = sumCbCb / skinCount - meanCb * meanCb
        val varCr = sumCrCr / skinCount - meanCr * meanCr
        val covCbCr = sumCbCr / skinCount - meanCb * meanCr

        // 2D spread = sqrt(det(covariance matrix)) = area of the spread ellipse
        val det = (varCb * varCr - covCbCr * covCbCr).coerceAtLeast(0f)
        val spread = sqrt(det)

        // Also compute individual standard deviations
        val stdCb = sqrt(varCb.coerceAtLeast(0f))
        val stdCr = sqrt(varCr.coerceAtLeast(0f))
        val avgStd = (stdCb + stdCr) / 2f

        // Real skin: avgStd ~6-12, spread ~20-80
        // Screen: avgStd ~2-5, spread ~4-15
        val stdScore = ((avgStd - 3f) / 7f).coerceIn(0f, 1f)
        val spreadScore = ((spread - 8f) / 40f).coerceIn(0f, 1f)

        return stdScore * 0.5f + spreadScore * 0.5f
    }

    // ---- Signal 3: Sharpness Uniformity ----

    /**
     * Analyze spatial variation of sharpness across the face crop.
     *
     * Real 3D faces have non-uniform sharpness across the crop because:
     * - Nose tip (protruding) is at a different focal distance than ears
     * - Eyes/brows have fine detail; cheeks are smoother
     * - Micro depth-of-field creates natural variation in local sharpness
     *
     * Screen-displayed faces have more uniform sharpness because:
     * - The entire screen surface is at one focal plane
     * - All pixels are equally sharp (or equally soft from camera focusing on screen)
     *
     * Method: Divide the face crop into a grid of blocks, compute Laplacian
     * variance per block, then measure the coefficient of variation (CV) across
     * blocks. High CV = non-uniform sharpness = real 3D face.
     * Low CV = uniform sharpness = flat surface (screen/print).
     *
     * @return Score [0..1]. High = non-uniform sharpness = more likely real.
     */
    private fun analyzeEdgeDofGradient(centerCrop: Bitmap, @Suppress("UNUSED_PARAMETER") widerCrop: Bitmap): Float {
        val sz = CROP_SIZE
        val pixels = IntArray(sz * sz)
        centerCrop.getPixels(pixels, 0, sz, 0, 0, sz, sz)

        val gray = FloatArray(sz * sz) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
        }

        // Divide into GRID_SIZE x GRID_SIZE blocks
        val gridSize = SHARPNESS_GRID_SIZE
        val blockSize = sz / gridSize
        val blockSharpness = FloatArray(gridSize * gridSize)

        for (by in 0 until gridSize) {
            for (bx in 0 until gridSize) {
                var sum = 0f
                var sumSq = 0f
                var count = 0
                val yStart = by * blockSize + 1
                val yEnd = ((by + 1) * blockSize).coerceAtMost(sz - 1)
                val xStart = bx * blockSize + 1
                val xEnd = ((bx + 1) * blockSize).coerceAtMost(sz - 1)
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val idx = y * sz + x
                        val lap = -4f * gray[idx] + gray[idx - 1] + gray[idx + 1] +
                            gray[idx - sz] + gray[idx + sz]
                        sum += lap
                        sumSq += lap * lap
                        count++
                    }
                }
                if (count > 0) {
                    val mean = sum / count
                    blockSharpness[by * gridSize + bx] = (sumSq / count - mean * mean).coerceAtLeast(0f)
                }
            }
        }

        // Compute coefficient of variation across blocks
        val n = blockSharpness.size
        val mean = blockSharpness.sum() / n
        if (mean < 10f) return 0.5f // Too low sharpness overall

        val variance = blockSharpness.map { (it - mean) * (it - mean) }.sum() / n
        val cv = sqrt(variance) / mean

        // Real 3D face: CV ~0.6-1.5+ (nose sharp, cheeks smooth, eyes detailed)
        // Screen: CV ~0.2-0.5 (uniform sharpness across flat surface)
        val score = ((cv - 0.35f) / 0.8f).coerceIn(0f, 1f)

        return score
    }

    // ---- Signal 4: Color Temperature Gradient ----

    /**
     * Analyze color temperature variation between bright and dark skin regions.
     *
     * Real 3D faces under mixed/ambient lighting have a color temperature gradient:
     * - Lit areas (highlights): warmer (more red/yellow) from warm ambient light
     *   sources (indoor lighting, sun)
     * - Shadow areas: cooler (more blue) from ambient sky/fill light
     * - This warm-highlight / cool-shadow pattern is a fundamental property of
     *   3D objects under natural lighting
     *
     * Screen-displayed faces lose this gradient because:
     * - The screen applies its own white point uniformly
     * - Double white-balance: original camera WB + viewing camera WB flattens
     *   the natural color temperature variation
     * - Screen backlight has a fixed color temperature across all pixels
     *
     * Method:
     * 1. Separate skin pixels into bright (top 30%) and dark (bottom 30%)
     * 2. Compute mean R/B ratio for each group (proxy for color temperature)
     * 3. Large difference = natural color temp gradient = real
     * 4. Small difference = uniform color temp = screen
     *
     * @return Score [0..1]. High = strong color temp gradient = more likely real.
     */
    private fun analyzeLightingConsistency(bitmap: Bitmap): Float {
        val pixels = IntArray(CROP_SIZE * CROP_SIZE)
        bitmap.getPixels(pixels, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)

        // Collect skin pixels with their luminance and R/B ratio
        data class SkinPixel(val lum: Float, val rbRatio: Float)
        val skinPixels = mutableListOf<SkinPixel>()

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val cb = 128f + (-0.169f * r - 0.331f * g + 0.500f * b)
            val cr = 128f + (0.500f * r - 0.419f * g - 0.081f * b)

            // Universal skin filter (Fitzpatrick I–VI)
            if (isSkin(lum, cb, cr)) {
                // R/B ratio as color temperature proxy (higher = warmer)
                val rbRatio = if (b > 5f) r / b else r / 5f
                skinPixels.add(SkinPixel(lum, rbRatio))
            }
        }

        if (skinPixels.size < MIN_SKIN_PIXELS) return 0.5f

        // Sort by luminance
        skinPixels.sortBy { it.lum }
        val n = skinPixels.size
        val darkCount = (n * 0.3f).toInt().coerceAtLeast(10)
        val brightCount = (n * 0.3f).toInt().coerceAtLeast(10)

        // Mean R/B ratio for dark (shadow) pixels
        val darkRb = skinPixels.subList(0, darkCount).map { it.rbRatio }.sum() / darkCount
        // Mean R/B ratio for bright (highlight) pixels
        val brightRb = skinPixels.subList(n - brightCount, n).map { it.rbRatio }.sum() / brightCount

        // Color temp difference: bright should be warmer (higher R/B) than dark
        // for real faces under typical indoor/outdoor lighting
        val ctDiff = brightRb - darkRb
        val ctAbsDiff = abs(ctDiff)

        // Real face: ctDiff ~0.15-0.6+ (highlights warmer than shadows)
        // Screen: ctDiff ~0.0-0.10 (uniform color temperature)
        val ctScore = ((ctAbsDiff - 0.05f) / 0.30f).coerceIn(0f, 1f)

        // Bonus if the gradient goes in the expected direction (highlights warmer)
        val directionBonus = if (ctDiff > 0.05f) 0.15f else 0f

        return (ctScore * 0.85f + directionBonus).coerceIn(0f, 1f)
    }

    // ---- Helpers ----

    /**
     * Universal skin detection covering Fitzpatrick I–VI.
     *
     * Uses a two-region approach in YCbCr:
     * - Primary range: Chai & Ngan (1999) — covers light to medium skin (I–IV)
     * - Dark skin extension: covers brown to very dark skin (V–VI) where
     *   Cb shifts higher (128–145) and Cr shifts lower (118–155)
     */
    @VisibleForTesting
    internal fun isSkin(y: Float, cb: Float, cr: Float): Boolean {
        if (y < 16f || y > 235f) return false
        // Primary range (Fitzpatrick I–IV)
        val primary = cb in 77f..127f && cr in 133f..173f
        // Dark skin extension (Fitzpatrick V–VI): lower Y, higher Cb, lower Cr
        val darkExt = y < 120f && cb in 80f..145f && cr in 118f..155f
        return primary || darkExt
    }

    private fun cropFace(
        bitmap: Bitmap, faceBbox: FaceDetection.BBox, targetSize: Int, margin: Float
    ): Bitmap {
        val imgW = bitmap.width
        val imgH = bitmap.height
        val faceCX = (faceBbox.left + faceBbox.right) / 2f * imgW
        val faceCY = (faceBbox.top + faceBbox.bottom) / 2f * imgH
        val faceW = faceBbox.width() * imgW * margin
        val faceH = faceBbox.height() * imgH * margin

        val left = (faceCX - faceW / 2f).toInt().coerceIn(0, imgW - 1)
        val top = (faceCY - faceH / 2f).toInt().coerceIn(0, imgH - 1)
        val right = (faceCX + faceW / 2f).toInt().coerceIn(left + 1, imgW)
        val bottom = (faceCY + faceH / 2f).toInt().coerceIn(top + 1, imgH)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (scaled !== cropped && cropped !== bitmap) cropped.recycle()
        return scaled
    }

    companion object {
        private const val CROP_SIZE = 80

        // Specular highlight thresholds
        private const val SPECULAR_OFFSET = 60f
        private const val MIN_HIGHLIGHT_PIXELS = 5
        private const val MAX_HIGHLIGHT_RATIO = 0.12f

        // Chrominance
        private const val MIN_SKIN_PIXELS = 100

        // Sharpness uniformity
        private const val SHARPNESS_GRID_SIZE = 4

        // Signal fusion weights
        private const val WEIGHT_SPECULAR = 0.25f
        private const val WEIGHT_CHROMINANCE = 0.25f
        private const val WEIGHT_EDGE_DOF = 0.25f
        private const val WEIGHT_LIGHTING = 0.25f
    }
}
