package com.openpad.core.frequency

import org.junit.Assert.assertEquals
import org.junit.Test

class FftMoireDetectorTest {

    private val detector = FftMoireDetector()

    @Test
    fun fft1DSingleElementNoOp() {
        val real = doubleArrayOf(5.0)
        val imag = doubleArrayOf(0.0)
        detector.fft1D(real, imag)
        assertEquals(5.0, real[0], 1e-10)
        assertEquals(0.0, imag[0], 1e-10)
    }

    @Test
    fun fft1DConstantSignalHasOnlyDCComponent() {
        val n = 8
        val real = DoubleArray(n) { 3.0 }
        val imag = DoubleArray(n) { 0.0 }
        detector.fft1D(real, imag)
        // DC component = N * value
        assertEquals(24.0, real[0], 1e-10)
        assertEquals(0.0, imag[0], 1e-10)
        // All other bins should be zero
        for (i in 1 until n) {
            assertEquals("real[$i] should be ~0", 0.0, real[i], 1e-8)
            assertEquals("imag[$i] should be ~0", 0.0, imag[i], 1e-8)
        }
    }

    @Test
    fun fft1DPreservesEnergy() {
        val n = 16
        val real = DoubleArray(n) { i -> kotlin.math.sin(2.0 * Math.PI * i / n) }
        val imag = DoubleArray(n) { 0.0 }
        val timeEnergy = real.sumOf { it * it }

        detector.fft1D(real, imag)
        val freqEnergy = (real.indices).sumOf { real[it] * real[it] + imag[it] * imag[it] } / n

        assertEquals(timeEnergy, freqEnergy, 1e-6)
    }
}
