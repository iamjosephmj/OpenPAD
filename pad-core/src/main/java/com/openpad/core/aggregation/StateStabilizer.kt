package com.openpad.core.aggregation

import timber.log.Timber

/**
 * Hysteresis state machine for PAD status transitions.
 *
 * Prevents flicker by requiring consecutive frames before committing a state change.
 * Ported from PadStateStabilizer — stripped of Dagger annotations.
 *
 * Special rules:
 * - NO_FACE transitions immediately (user needs instant feedback).
 * - ANALYZING does not change the current state (transient).
 * - LIVE requires [exitConsecutive] consecutive frames to enter.
 * - SPOOF_SUSPECTED requires [enterConsecutive] consecutive frames.
 */
class StateStabilizer(initial: PadStatus = PadStatus.ANALYZING) {

    var current: PadStatus = initial
        private set

    private var candidate: PadStatus = initial
    private var consecutive: Int = 0

    fun update(
        newCandidate: PadStatus,
        enterConsecutive: Int,
        exitConsecutive: Int
    ): PadStatus {
        // ANALYZING is transient — don't reset candidate counter
        if (newCandidate == PadStatus.ANALYZING) return current

        // NO_FACE transitions immediately
        if (newCandidate == PadStatus.NO_FACE) {
            commit(PadStatus.NO_FACE)
            return current
        }

        if (newCandidate == current) {
            val cap = maxOf(enterConsecutive, exitConsecutive)
            consecutive = minOf(consecutive + 1, cap)
            return current
        }

        val needed = when {
            newCandidate == PadStatus.LIVE -> exitConsecutive
            current == PadStatus.LIVE -> enterConsecutive
            else -> enterConsecutive
        }

        if (newCandidate != candidate) {
            candidate = newCandidate
            consecutive = 1
        } else {
            consecutive++
        }

        if (consecutive >= needed) {
            commit(newCandidate)
        }

        return current
    }

    fun reset() {
        current = PadStatus.ANALYZING
        candidate = PadStatus.ANALYZING
        consecutive = 0
    }

    private fun commit(newStatus: PadStatus) {
        val prev = current
        current = newStatus
        candidate = newStatus
        consecutive = 0
        if (prev != newStatus) {
            Timber.tag(TAG).d("Stabilizer: %s → %s", prev, newStatus)
        }
    }

    companion object {
        private const val TAG = "PAD"
    }
}
