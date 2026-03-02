package com.openpad.core.frequency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FftMoireDetectorTest {

    private val detector = FftMoireDetector()

    @Test
    fun `FFT of single frequency produces peak at correct bin`() {
        val n = 64
        val freq = 8.0 // 8 cycles
        val real = DoubleArray(n) { cos(2.0 * PI * freq * it / n) }
        val imag = DoubleArray(n)

        detector.fft1D(real, imag)

        // Peak should be at bin 8
        var peakBin = 0
        var peakMag = 0.0
        for (i in 1 until n / 2) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            if (mag > peakMag) {
                peakMag = mag
                peakBin = i
            }
        }

        assertEquals("Peak should be at bin $freq", freq.toInt(), peakBin)
        assertTrue("Peak magnitude should be significant", peakMag > n / 4)
    }

    @Test
    fun `FFT of DC signal produces energy only at bin 0`() {
        val n = 64
        val real = DoubleArray(n) { 1.0 }
        val imag = DoubleArray(n)

        detector.fft1D(real, imag)

        // DC component should dominate
        val dcMag = sqrt(real[0] * real[0] + imag[0] * imag[0])
        assertTrue("DC magnitude should be n", dcMag > n * 0.9)

        // Other bins should be negligible
        for (i in 1 until n) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            assertTrue("Non-DC bin $i should be near zero, was $mag", mag < 0.01)
        }
    }

    @Test
    fun `FFT of two frequencies produces peaks at both bins`() {
        val n = 64
        val freq1 = 4.0
        val freq2 = 16.0
        val real = DoubleArray(n) {
            cos(2.0 * PI * freq1 * it / n) + cos(2.0 * PI * freq2 * it / n)
        }
        val imag = DoubleArray(n)

        detector.fft1D(real, imag)

        val magnitudes = (0 until n / 2).map { i ->
            sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        // Both frequency bins should have significant energy
        assertTrue("Bin 4 should have energy", magnitudes[4] > n / 8)
        assertTrue("Bin 16 should have energy", magnitudes[16] > n / 8)
    }

    @Test
    fun `FFT roundtrip preserves signal`() {
        val n = 64
        val original = DoubleArray(n) { sin(2.0 * PI * 5 * it / n) + 0.5 * cos(2.0 * PI * 12 * it / n) }
        val real = original.copyOf()
        val imag = DoubleArray(n)

        // Forward FFT
        detector.fft1D(real, imag)

        // Inverse FFT (conjugate + FFT + conjugate + scale)
        for (i in imag.indices) imag[i] = -imag[i]
        detector.fft1D(real, imag)
        for (i in real.indices) {
            real[i] /= n
            imag[i] = -imag[i] / n
        }

        // Should match original
        for (i in original.indices) {
            assertEquals("Sample $i should match", original[i], real[i], 1e-10)
        }
    }
}
