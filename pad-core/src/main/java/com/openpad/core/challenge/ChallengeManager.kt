package com.openpad.core.challenge

import com.openpad.core.PadResult

/** Layer 5 interface: manages challenge-response flow. */
interface ChallengeManager {
    val phase: ChallengePhase
    val evidence: ChallengeEvidence

    /**
     * Process a new frame result and advance the state machine.
     * @return Updated challenge phase.
     */
    fun onFrame(result: PadResult): ChallengePhase

    fun reset()

    /** Advance to LIVE (user passed evaluation). */
    fun advanceToLive()

    /** Handle spoof detection. Returns true if max attempts reached. */
    fun handleSpoof(): Boolean

    /** Advance to DONE (terminal). */
    fun advanceToDone()
}
