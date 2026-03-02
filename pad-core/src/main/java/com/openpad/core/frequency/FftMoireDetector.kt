package com.openpad.core.frequency

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Layer 3: Pure-Kotlin FFT-based moiré and halftone detection.
 *
 * Detects screen moiré patterns (interference fringes from pixel grids)
 * and print halftone (regular dot patterns) via frequency-domain analysis.
 *
 * Pipeline:
 * 1. Crop face region → scale to 64x64 grayscale
 * 2. Apply Hann window (reduce spectral leakage)
 * 3. 2D FFT via row-then-column Cooley-Tukey radix-2
 * 4. Compute power spectrum (magnitude squared)
 * 5. Analyze mid-frequency energy concentration (moiré indicator)
 * 6. Compute spectral flatness (Wiener entropy — low = tonal = screen/print)
 *
 * ~2ms per frame on modern ARM.
 */
class FftMoireDetector : FrequencyAnalyzer {

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FrequencyResult {
        val gray = cropAndGrayscale(bitmap, faceBbox)
        applyHannWindow(gray)

        // 2D FFT
        val real = Array(FFT_SIZE) { y -> DoubleArray(FFT_SIZE) { x -> gray[y * FFT_SIZE + x].toDouble() } }
        val imag = Array(FFT_SIZE) { DoubleArray(FFT_SIZE) }

        fft2D(real, imag)

        // Power spectrum (skip DC at [0,0])
        val power = DoubleArray(FFT_SIZE * FFT_SIZE)
        for (y in 0 until FFT_SIZE) {
            for (x in 0 until FFT_SIZE) {
                power[y * FFT_SIZE + x] = real[y][x] * real[y][x] + imag[y][x] * imag[y][x]
            }
        }
        power[0] = 0.0 // Zero DC component

        // Compute radial power spectrum
        val halfSize = FFT_SIZE / 2
        val radialBins = DoubleArray(halfSize)
        val radialCounts = IntArray(halfSize)

        for (y in 0 until FFT_SIZE) {
            for (x in 0 until FFT_SIZE) {
                val fy = if (y <= halfSize) y else y - FFT_SIZE
                val fx = if (x <= halfSize) x else x - FFT_SIZE
                val radius = sqrt((fx * fx + fy * fy).toDouble()).toInt()
                if (radius in 1 until halfSize) {
                    radialBins[radius] += power[y * FFT_SIZE + x]
                    radialCounts[radius]++
                }
            }
        }

        // Normalize radial bins
        for (i in radialBins.indices) {
            if (radialCounts[i] > 0) radialBins[i] /= radialCounts[i]
        }

        // Moiré score: ratio of mid-frequency energy to total
        val totalEnergy = radialBins.sum()
        val midLow = halfSize / 4     // ~8
        val midHigh = halfSize * 3 / 4 // ~24
        val midEnergy = radialBins.slice(midLow until midHigh).sum()
        val moireScore = if (totalEnergy > 0) (midEnergy / totalEnergy).toFloat() else 0f

        // Peak frequency
        var peakIdx = 1
        var peakVal = 0.0
        for (i in 1 until halfSize) {
            if (radialBins[i] > peakVal) {
                peakVal = radialBins[i]
                peakIdx = i
            }
        }

        // Spectral flatness (Wiener entropy): geometric mean / arithmetic mean
        // Low flatness = tonal spectrum (screen/print), high = noise-like (natural)
        val nonZeroBins = radialBins.filter { it > 0 }
        val spectralFlatness = if (nonZeroBins.size > 1) {
            val logSum = nonZeroBins.sumOf { ln(it) }
            val geometricMean = kotlin.math.exp(logSum / nonZeroBins.size)
            val arithmeticMean = nonZeroBins.average()
            if (arithmeticMean > 0) (geometricMean / arithmeticMean).toFloat().coerceIn(0f, 1f)
            else 1f
        } else {
            1f
        }

        return FrequencyResult(
            moireScore = moireScore.coerceIn(0f, 1f),
            peakFrequency = peakIdx.toFloat(),
            spectralFlatness = spectralFlatness
        )
    }

    /**
     * Crop the face region and convert to 64x64 grayscale float array.
     */
    private fun cropAndGrayscale(bitmap: Bitmap, faceBbox: FaceDetection.BBox): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val left = (faceBbox.left * w).toInt().coerceIn(0, w - 1)
        val top = (faceBbox.top * h).toInt().coerceIn(0, h - 1)
        val right = (faceBbox.right * w).toInt().coerceIn(left + 1, w)
        val bottom = (faceBbox.bottom * h).toInt().coerceIn(top + 1, h)

        val cropW = right - left
        val cropH = bottom - top
        if (cropW < 2 || cropH < 2) return FloatArray(FFT_SIZE * FFT_SIZE)

        val faceCrop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(faceCrop, FFT_SIZE, FFT_SIZE, true)

        val pixels = IntArray(FFT_SIZE * FFT_SIZE)
        scaled.getPixels(pixels, 0, FFT_SIZE, 0, 0, FFT_SIZE, FFT_SIZE)

        return FloatArray(pixels.size) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
        }
    }

    /**
     * Apply Hann window to reduce spectral leakage.
     */
    private fun applyHannWindow(data: FloatArray) {
        for (y in 0 until FFT_SIZE) {
            val wy = 0.5f * (1f - cos(2.0 * PI * y / FFT_SIZE).toFloat())
            for (x in 0 until FFT_SIZE) {
                val wx = 0.5f * (1f - cos(2.0 * PI * x / FFT_SIZE).toFloat())
                data[y * FFT_SIZE + x] *= wy * wx
            }
        }
    }

    /**
     * 2D FFT via row-then-column approach.
     * Applies 1D FFT to each row, then to each column.
     */
    private fun fft2D(real: Array<DoubleArray>, imag: Array<DoubleArray>) {
        val n = real.size

        // FFT rows
        for (y in 0 until n) {
            fft1D(real[y], imag[y])
        }

        // FFT columns (transpose, FFT rows, transpose back)
        val colReal = DoubleArray(n)
        val colImag = DoubleArray(n)
        for (x in 0 until n) {
            for (y in 0 until n) {
                colReal[y] = real[y][x]
                colImag[y] = imag[y][x]
            }
            fft1D(colReal, colImag)
            for (y in 0 until n) {
                real[y][x] = colReal[y]
                imag[y][x] = colImag[y]
            }
        }
    }

    /**
     * In-place Cooley-Tukey radix-2 FFT.
     * Input arrays must have power-of-2 length.
     */
    internal fun fft1D(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
        }

        // Butterfly stages
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wReal = cos(angle)
            val wImag = sin(angle)

            var i = 0
            while (i < n) {
                var curWReal = 1.0
                var curWImag = 0.0
                for (k in 0 until halfLen) {
                    val evenIdx = i + k
                    val oddIdx = i + k + halfLen
                    val tReal = curWReal * real[oddIdx] - curWImag * imag[oddIdx]
                    val tImag = curWReal * imag[oddIdx] + curWImag * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] = real[evenIdx] + tReal
                    imag[evenIdx] = imag[evenIdx] + tImag
                    val newWReal = curWReal * wReal - curWImag * wImag
                    curWImag = curWReal * wImag + curWImag * wReal
                    curWReal = newWReal
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        private const val FFT_SIZE = 64
    }
}
