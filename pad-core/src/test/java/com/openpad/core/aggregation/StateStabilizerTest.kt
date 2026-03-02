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
    fun `initial state is ANALYZING`() {
        assertEquals(PadStatus.ANALYZING, stabilizer.current)
    }

    @Test
    fun `NO_FACE transitions immediately`() {
        stabilizer.update(PadStatus.NO_FACE, enterConsecutive = 5, exitConsecutive = 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)
    }

    @Test
    fun `ANALYZING does not change state`() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.ANALYZING, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)
    }

    @Test
    fun `LIVE requires exitConsecutive frames to enter`() {
        // Get out of ANALYZING first
        stabilizer.update(PadStatus.NO_FACE, 5, 8)

        // LIVE needs 8 consecutive
        repeat(7) {
            stabilizer.update(PadStatus.LIVE, 5, 8)
        }
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.LIVE, 5, 8)
        assertEquals(PadStatus.LIVE, stabilizer.current)
    }

    @Test
    fun `SPOOF_SUSPECTED requires enterConsecutive frames`() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)

        repeat(4) {
            stabilizer.update(PadStatus.SPOOF_SUSPECTED, 5, 8)
        }
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.SPOOF_SUSPECTED, 5, 8)
        assertEquals(PadStatus.SPOOF_SUSPECTED, stabilizer.current)
    }

    @Test
    fun `interrupting candidate resets counter`() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)

        repeat(3) {
            stabilizer.update(PadStatus.LIVE, 5, 8)
        }
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        // Interrupt with SPOOF_SUSPECTED
        stabilizer.update(PadStatus.SPOOF_SUSPECTED, 5, 8)
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        // Now LIVE counter should restart
        repeat(7) {
            stabilizer.update(PadStatus.LIVE, 5, 8)
        }
        assertEquals(PadStatus.NO_FACE, stabilizer.current)

        stabilizer.update(PadStatus.LIVE, 5, 8)
        assertEquals(PadStatus.LIVE, stabilizer.current)
    }

    @Test
    fun `exiting LIVE requires enterConsecutive`() {
        // Enter LIVE
        stabilizer.update(PadStatus.NO_FACE, 5, 8)
        repeat(8) { stabilizer.update(PadStatus.LIVE, 5, 8) }
        assertEquals(PadStatus.LIVE, stabilizer.current)

        // Exiting LIVE to SPOOF needs enterConsecutive (5) frames
        repeat(4) {
            stabilizer.update(PadStatus.SPOOF_SUSPECTED, 5, 8)
        }
        assertEquals(PadStatus.LIVE, stabilizer.current)

        stabilizer.update(PadStatus.SPOOF_SUSPECTED, 5, 8)
        assertEquals(PadStatus.SPOOF_SUSPECTED, stabilizer.current)
    }

    @Test
    fun `reset returns to ANALYZING`() {
        stabilizer.update(PadStatus.NO_FACE, 5, 8)
        repeat(8) { stabilizer.update(PadStatus.LIVE, 5, 8) }
        assertEquals(PadStatus.LIVE, stabilizer.current)

        stabilizer.reset()
        assertEquals(PadStatus.ANALYZING, stabilizer.current)
    }
}
