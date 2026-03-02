package com.openpad.core.aggregation

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StateStabilizerTest {

    private lateinit var stabilizer: StateStabilizer

    @Before
    fun setup() {
        stabilizer = StateStabilizer()
    }

    @Test
    fun initialStateIsAnalyzing() {
        assertEquals(PadStatus.ANALYZING, stabilizer.current)
    }

    @Test
    fun noFaceTransitionsImmediately() {
        stabilizer.update(PadStatus.NO_FACE, enterConsecutive = 5, exitConsecutive = 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)
    }

    @Test
    fun analyzingDoesNotChangeState() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.ANALYZING, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)
    }

    @Test
    fun liveRequiresExitConsecutiveFramesToEnter() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)

        repeat(7) {
            stabilizer.update(PadStatus.LIVE, 5, 8)
        }
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.LIVE, 5, 8)
        assertEquals(PadStatus.LIVE, stabilizer.current)
    }

    @Test
    fun spoofSuspectedRequiresEnterConsecutiveFrames() {
        stabilizer.update(PadStatus.LIVE, enterConsecutive = 3, exitConsecutive = 2)

        repeat(2) {
            stabilizer.update(PadStatus.SPOOF_SUSPECTED, 3, 2)
        }
        assertEquals(PadStatus.LIVE, stabilizer.current)

        stabilizer.update(PadStatus.SPOOF_SUSPECTED, 3, 2)
        assertEquals(PadStatus.SPOOF_SUSPECTED, stabilizer.current)
    }

    @Test
    fun resetReturnsToAnalyzing() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.reset()
        assertEquals(PadStatus.ANALYZING, stabilizer.current)
    }
}
