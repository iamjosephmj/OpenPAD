package com.openpad.core.photometric

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotometricAnalyzerTest {

    private lateinit var analyzer: PhotometricAnalyzer

    @Before
    fun setup() {
        analyzer = PhotometricAnalyzer()
    }

    // ---- Fitzpatrick I–IV: Primary range (Cb 77–127, Cr 133–173) ----

    @Test
    fun `isSkin detects Fitzpatrick I - very light skin`() {
        // Very fair skin: high luminance, moderate Cb, moderate-high Cr
        assertTrue(analyzer.isSkin(y = 180f, cb = 105f, cr = 155f))
    }

    @Test
    fun `isSkin detects Fitzpatrick II - light skin`() {
        assertTrue(analyzer.isSkin(y = 160f, cb = 110f, cr = 150f))
    }

    @Test
    fun `isSkin detects Fitzpatrick III - medium skin`() {
        assertTrue(analyzer.isSkin(y = 140f, cb = 115f, cr = 145f))
    }

    @Test
    fun `isSkin detects Fitzpatrick IV - olive skin`() {
        // Olive/tan skin: lower Y, Cb approaching upper primary bound
        assertTrue(analyzer.isSkin(y = 120f, cb = 120f, cr = 140f))
    }

    // ---- Fitzpatrick V–VI: Dark skin extension (Y<120, Cb 80–145, Cr 118–155) ----

    @Test
    fun `isSkin detects Fitzpatrick V - brown skin`() {
        // Brown skin: Cb above primary max (130 > 127), caught by dark extension
        assertTrue(analyzer.isSkin(y = 90f, cb = 130f, cr = 135f))
    }

    @Test
    fun `isSkin detects Fitzpatrick VI - dark brown skin`() {
        // Very dark skin: low Y, high Cb, low Cr — only dark extension catches this
        assertTrue(analyzer.isSkin(y = 60f, cb = 140f, cr = 125f))
    }

    @Test
    fun `isSkin detects Fitzpatrick VI - very dark skin at low luminance`() {
        // Darkest skin tones: Y as low as 25-30
        assertTrue(analyzer.isSkin(y = 30f, cb = 135f, cr = 120f))
    }

    @Test
    fun `isSkin detects dark skin at Cb upper boundary`() {
        // Cb = 145 is the upper bound of dark extension
        assertTrue(analyzer.isSkin(y = 70f, cb = 145f, cr = 130f))
    }

    @Test
    fun `isSkin detects dark skin at Cr lower boundary`() {
        // Cr = 118 is the lower bound of dark extension
        assertTrue(analyzer.isSkin(y = 80f, cb = 130f, cr = 118f))
    }

    // ---- Negative tests: non-skin colors ----

    @Test
    fun `isSkin rejects blue sky`() {
        // Blue: very high Cb, very low Cr
        assertFalse(analyzer.isSkin(y = 150f, cb = 180f, cr = 90f))
    }

    @Test
    fun `isSkin rejects green foliage`() {
        // Green: low Cb, low Cr
        assertFalse(analyzer.isSkin(y = 130f, cb = 60f, cr = 100f))
    }

    @Test
    fun `isSkin rejects white background`() {
        // White: Y very high, Cb and Cr near 128 (neutral) — Cr below skin range
        assertFalse(analyzer.isSkin(y = 230f, cb = 128f, cr = 128f))
    }

    @Test
    fun `isSkin rejects black hair`() {
        // Very dark non-skin: Y below minimum
        assertFalse(analyzer.isSkin(y = 10f, cb = 128f, cr = 128f))
    }

    @Test
    fun `isSkin rejects red clothing`() {
        // Red: very high Cr, but Cb too low for skin
        assertFalse(analyzer.isSkin(y = 100f, cb = 60f, cr = 190f))
    }

    @Test
    fun `isSkin rejects yellow object`() {
        // Yellow: low Cb, moderate Cr
        assertFalse(analyzer.isSkin(y = 200f, cb = 50f, cr = 145f))
    }

    // ---- Boundary tests ----

    @Test
    fun `isSkin rejects Y below minimum`() {
        assertFalse(analyzer.isSkin(y = 15f, cb = 110f, cr = 150f))
    }

    @Test
    fun `isSkin rejects Y above maximum`() {
        assertFalse(analyzer.isSkin(y = 236f, cb = 110f, cr = 150f))
    }

    @Test
    fun `isSkin accepts Y at lower boundary`() {
        // Y = 16 is the minimum, with valid skin Cb/Cr in dark extension
        assertTrue(analyzer.isSkin(y = 16f, cb = 130f, cr = 130f))
    }

    @Test
    fun `isSkin accepts Y at upper boundary`() {
        // Y = 235 is the maximum, with valid primary skin Cb/Cr
        assertTrue(analyzer.isSkin(y = 235f, cb = 100f, cr = 150f))
    }

    @Test
    fun `isSkin rejects Cb just above dark extension upper bound`() {
        // Cb = 146 is outside dark extension max of 145
        // Also outside primary max of 127
        assertFalse(analyzer.isSkin(y = 80f, cb = 146f, cr = 130f))
    }

    @Test
    fun `isSkin rejects Cr just below dark extension lower bound`() {
        // Cr = 117 is outside dark extension min of 118
        // Also outside primary min of 133
        assertFalse(analyzer.isSkin(y = 80f, cb = 130f, cr = 117f))
    }

    @Test
    fun `isSkin primary range at exact boundaries`() {
        // Cb = 77 (min), Cr = 133 (min) — just inside primary
        assertTrue(analyzer.isSkin(y = 150f, cb = 77f, cr = 133f))
        // Cb = 127 (max), Cr = 173 (max) — just inside primary
        assertTrue(analyzer.isSkin(y = 150f, cb = 127f, cr = 173f))
    }

    // ---- Dark extension does NOT activate at high luminance ----

    @Test
    fun `isSkin dark extension requires low Y`() {
        // Cb = 140 is outside primary (max 127), needs dark extension
        // But Y = 130 > 120, so dark extension doesn't activate
        assertFalse(analyzer.isSkin(y = 130f, cb = 140f, cr = 130f))
    }

    @Test
    fun `isSkin dark extension activates at Y boundary`() {
        // Same Cb/Cr but Y = 119 < 120 — dark extension activates
        assertTrue(analyzer.isSkin(y = 119f, cb = 140f, cr = 130f))
    }
}
