package com.openpad.core.photometric

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotometricIsSkinTest {

    private val analyzer = PhotometricAnalyzer()

    @Test
    fun isSkinReturnsTrueForTypicalSkinTone() {
        // Fitzpatrick I-IV range: Y=150, Cb=110, Cr=155
        assertTrue(analyzer.isSkin(150f, 110f, 155f))
    }

    @Test
    fun isSkinReturnsFalseForOutOfRangeY() {
        // Y below 16 is out of range
        assertFalse(analyzer.isSkin(10f, 110f, 155f))
        // Y above 235 is out of range
        assertFalse(analyzer.isSkin(240f, 110f, 155f))
    }

    @Test
    fun isSkinReturnsTrueForDarkSkin() {
        // Dark skin extension: lower Y, broader Cb/Cr
        assertTrue(analyzer.isSkin(100f, 90f, 140f))
    }

    @Test
    fun isSkinReturnsFalseForNonSkinColor() {
        // Blue-ish values far outside skin ranges
        assertFalse(analyzer.isSkin(200f, 200f, 50f))
    }
}
