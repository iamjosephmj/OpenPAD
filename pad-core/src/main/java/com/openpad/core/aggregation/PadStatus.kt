package com.openpad.core.aggregation

/** Per-frame PAD status from the classifier + stabilizer. */
enum class PadStatus {
    ANALYZING,
    NO_FACE,
    LIVE,
    SPOOF_SUSPECTED,
    COMPLETED;

    companion object {
        fun fromInt(v: Int): PadStatus = when (v) {
            0 -> ANALYZING
            1 -> NO_FACE
            2 -> LIVE
            3 -> SPOOF_SUSPECTED
            4 -> COMPLETED
            else -> ANALYZING
        }
    }
}
