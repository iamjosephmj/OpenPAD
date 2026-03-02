/**
 * @file aggregation.h
 * @brief Classification, score aggregation, and state stabilization.
 *
 * ### Classifier (`opad_classify`)
 * Sequential decision gates that short-circuit on clear spoof signals:
 *   1. Minimum frames / face frames check → ANALYZING
 *   2. Device overlap with high confidence  → SPOOF_SUSPECTED
 *   3. Moiré AND LBP both above threshold   → SPOOF_SUSPECTED
 *   4. Texture below threshold              → SPOOF_SUSPECTED
 *   5. CDCN depth below threshold           → SPOOF_SUSPECTED
 *   6. Photometric below threshold          → SPOOF_SUSPECTED
 *   7. Texture passes                       → LIVE
 *
 * ### Score Aggregator (`opad_compute_aggregate_score`)
 * Weighted combination of texture, MN3, CDCN, and device scores. If CDCN
 * is unavailable, its weight is redistributed (2/3 to texture, 1/3 to MN3).
 *
 * ### State Stabilizer (`OpadStateStabilizer`)
 * Hysteresis filter requiring N consecutive same-status frames before
 * committing a transition. Prevents flickering on noisy per-frame outputs.
 */

#ifndef OPENPAD_AGGREGATION_H
#define OPENPAD_AGGREGATION_H

#include "types.h"

/**
 * Classify a single frame through sequential decision gates.
 * @return Per-frame (unstabilized) PAD status.
 */
OpadPadStatus opad_classify(const OpadConfig* config,
                             const OpadTextureInput* texture,
                             const OpadDepthInput* depth,
                             const OpadFrequencyInput* frequency,
                             const OpadDeviceInput* device,
                             const OpadPhotometricInput* photometric,
                             const OpadTemporalFeatures* temporal);

/**
 * Compute a weighted aggregate genuine-probability score in [0, 1].
 * @return Aggregate score; higher = more likely genuine.
 */
float opad_compute_aggregate_score(const OpadConfig* config,
                                    const OpadTextureInput* texture,
                                    const OpadDepthInput* depth,
                                    const OpadDeviceInput* device);

/** Opaque state stabilizer handle. */
typedef struct OpadStateStabilizer OpadStateStabilizer;

/** Create a stabilizer starting in @p initial status. */
OpadStateStabilizer* opad_stabilizer_create(OpadPadStatus initial);

/** Free the stabilizer. */
void opad_stabilizer_destroy(OpadStateStabilizer* s);

/**
 * Feed a per-frame candidate status and return the stabilized status.
 *
 * The stabilizer requires `exit_consecutive` consecutive LIVE frames
 * to transition to LIVE, and `enter_consecutive` frames for other states.
 */
OpadPadStatus opad_stabilizer_update(OpadStateStabilizer* s,
                                      OpadPadStatus candidate,
                                      int32_t enter_consecutive,
                                      int32_t exit_consecutive);

/** Reset the stabilizer to ANALYZING with zero consecutive count. */
void opad_stabilizer_reset(OpadStateStabilizer* s);

#endif /* OPENPAD_AGGREGATION_H */
