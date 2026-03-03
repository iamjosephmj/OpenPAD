package com.openpad.core.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceDetectionTest {

    @Test
    fun centerXIsCorrect() {
        val detection = FaceDetection(
            confidence = 0.9f,
            bbox = FaceDetection.BBox(0.1f, 0.2f, 0.5f, 0.8f)
        )
        assertEquals(0.3f, detection.centerX, 1e-6f)
    }

    @Test
    fun centerYIsCorrect() {
        val detection = FaceDetection(
            confidence = 0.9f,
            bbox = FaceDetection.BBox(0.1f, 0.2f, 0.5f, 0.8f)
        )
        assertEquals(0.5f, detection.centerY, 1e-6f)
    }

    @Test
    fun areaIsCorrect() {
        val detection = FaceDetection(
            confidence = 0.9f,
            bbox = FaceDetection.BBox(0.0f, 0.0f, 0.5f, 0.4f)
        )
        // width=0.5, height=0.4, area=0.2
        assertEquals(0.2f, detection.area, 1e-6f)
    }

    @Test
    fun bboxWidthHeight() {
        val bbox = FaceDetection.BBox(0.1f, 0.2f, 0.7f, 0.9f)
        assertEquals(0.6f, bbox.width(), 1e-6f)
        assertEquals(0.7f, bbox.height(), 1e-6f)
    }
}
