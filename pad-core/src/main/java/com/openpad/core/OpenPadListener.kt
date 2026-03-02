package com.openpad.core

/**
 * Callback interface for receiving results from an OpenPad analysis session.
 *
 * Exactly one of these methods will be called per [OpenPad.analyze] invocation.
 * All callbacks are delivered on the main thread.
 */
interface OpenPadListener {
    /** The subject was confirmed as a live person. */
    fun onLiveConfirmed(result: OpenPadResult)

    /** A presentation attack (spoof) was detected. */
    fun onSpoofDetected(result: OpenPadResult)

    /** An error occurred during the analysis session. */
    fun onError(error: OpenPadError)

    /** The user cancelled the verification flow. */
    fun onCancelled()
}
