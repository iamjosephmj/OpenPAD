package com.openpad.core.aggregation

/** Per-frame PAD status from the classifier + stabilizer. */
enum class PadStatus {
    ANALYZING,
    NO_FACE,
    LIVE,
    SPOOF_SUSPECTED,
    COMPLETED
}
