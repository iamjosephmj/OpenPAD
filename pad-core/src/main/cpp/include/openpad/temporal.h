/**
 * @file temporal.h
 * @brief Temporal feature tracking over a sliding window.
 *
 * Maintains fixed-size ring buffers for face center, area, and confidence
 * across consecutive frames. Computes:
 *
 * - **Movement variance** — head motion magnitude (high = natural movement).
 * - **Movement smoothness** — inverse of average acceleration (jerky = spoof).
 * - **Blink detection** — confidence dip + recovery, or area dip + recovery.
 * - **Face size stability** — variance of the face area (too stable = static).
 *
 * The opaque `OpadTemporalTracker` owns the ring buffers and is created/
 * destroyed via the standard create/destroy API pattern.
 */

#ifndef OPENPAD_TEMPORAL_H
#define OPENPAD_TEMPORAL_H

#include "types.h"

/** Opaque temporal tracker handle. */
typedef struct OpadTemporalTracker OpadTemporalTracker;

/**
 * Create a tracker with ring buffer capacity from config.sliding_window_size.
 * @return Heap-allocated tracker, or NULL on allocation failure.
 */
OpadTemporalTracker* opad_temporal_tracker_create(const OpadConfig* config);

/** Free the tracker. Safe to call with NULL. */
void opad_temporal_tracker_destroy(OpadTemporalTracker* t);

/**
 * Feed one frame into the tracker and compute all temporal features.
 *
 * @param t               The tracker.
 * @param det             Face detection for this frame, or NULL if no face.
 * @param frame_similarity Similarity to the previous frame [0,1].
 * @param out             Filled with all computed temporal features.
 */
void opad_temporal_tracker_update(OpadTemporalTracker* t,
                                   const OpadFaceDetection* det,
                                   float frame_similarity,
                                   OpadTemporalFeatures* out);

/** Reset all ring buffers and counters (e.g. on session restart). */
void opad_temporal_tracker_reset(OpadTemporalTracker* t);

#endif /* OPENPAD_TEMPORAL_H */
