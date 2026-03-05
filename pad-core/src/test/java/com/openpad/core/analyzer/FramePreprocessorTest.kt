package com.openpad.core.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePreprocessorTest {

    // ── Gamma LUT tests ─────────────────────────────────────────────────

    @Test
    fun gammaLutIsNullWhenLuminanceMatchesTarget() {
        val lut = FramePreprocessor.buildGammaLut(luminance = 0.45f, gammaTarget = 0.45f)
        assertNull("Identity gamma should return null", lut)
    }

    @Test
    fun gammaLutBrightensForDarkFrame() {
        val lut = FramePreprocessor.buildGammaLut(luminance = 0.15f, gammaTarget = 0.45f)
        assertNotNull("Dark frame should produce a brightening LUT", lut)
        lut!!
        assertTrue("Mid-tone 128 should be brighter after gamma", lut[128] > 128)
        assertEquals("Black stays black", 0, lut[0])
        assertEquals("White stays white", 255, lut[255])
    }

    @Test
    fun gammaLutDarkensForBrightFrame() {
        val lut = FramePreprocessor.buildGammaLut(luminance = 0.85f, gammaTarget = 0.45f)
        assertNotNull("Bright frame should produce a darkening LUT", lut)
        lut!!
        assertTrue("Mid-tone 128 should be darker after gamma", lut[128] < 128)
        assertEquals("Black stays black", 0, lut[0])
        assertEquals("White stays white", 255, lut[255])
    }

    @Test
    fun gammaLutMonotonicallyIncreasing() {
        val lut = FramePreprocessor.buildGammaLut(luminance = 0.20f, gammaTarget = 0.45f)!!
        for (i in 1 until 256) {
            assertTrue("LUT must be monotonically increasing at index $i", lut[i] >= lut[i - 1])
        }
    }

    @Test
    fun gammaLutBoundsClampedForExtremeLuminance() {
        val lutDark = FramePreprocessor.buildGammaLut(luminance = 0.01f, gammaTarget = 0.45f)
        assertNotNull(lutDark)
        for (v in lutDark!!) {
            assertTrue("All LUT values must be in [0, 255]", v in 0..255)
        }

        val lutBright = FramePreprocessor.buildGammaLut(luminance = 0.99f, gammaTarget = 0.45f)
        assertNotNull(lutBright)
        for (v in lutBright!!) {
            assertTrue("All LUT values must be in [0, 255]", v in 0..255)
        }
    }

    // ── Luminance helper ────────────────────────────────────────────────

    @Test
    fun luminanceFromPixelReturnsCorrectValues() {
        val white = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
        assertEquals(255, FramePreprocessor.luminanceFromPixel(white))

        val black = (0xFF shl 24)
        assertEquals(0, FramePreprocessor.luminanceFromPixel(black))

        val red = (0xFF shl 24) or (0xFF shl 16)
        val expectedRed = (255 * 77) shr 8
        assertEquals(expectedRed, FramePreprocessor.luminanceFromPixel(red))
    }

    @Test
    fun luminanceFromPixelIsInRange() {
        for (r in 0..255 step 51) {
            for (g in 0..255 step 51) {
                for (b in 0..255 step 51) {
                    val pixel = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val lum = FramePreprocessor.luminanceFromPixel(pixel)
                    assertTrue("Luminance $lum out of range for ($r,$g,$b)", lum in 0..255)
                }
            }
        }
    }

    // ── Config edge cases ───────────────────────────────────────────────

    @Test
    fun disabledGammaTargetSkipsGamma() {
        val lut = FramePreprocessor.buildGammaLut(luminance = 0.2f, gammaTarget = 0f)
        assertNull("gammaTarget=0 should be treated as disabled (near-identity)", lut)
    }
}
