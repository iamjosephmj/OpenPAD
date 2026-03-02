/**
 * @file pipeline.h
 * @brief Top-level pipeline orchestrator.
 *
 * The `OpadPipeline` owns all analysis modules (temporal tracker, state
 * stabilizer, challenge state machine) and orchestrates the per-frame
 * analysis flow:
 *
 * ```
 * Frame → Similarity → Sharpness → FFT Moiré → LBP Screen
 *       → Photometric → Temporal Tracking → Classification
 *       → Stabilization → Aggregation → Challenge → Output
 * ```
 *
 * The pipeline is created once per session with a config, and reset on
 * session restart. It is NOT thread-safe; the JNI layer guards access
 * with a pthread mutex.
 *
 * @see openpad_jni.c for the thread-safe JNI wrapper.
 */

#ifndef OPENPAD_PIPELINE_H
#define OPENPAD_PIPELINE_H

#include "types.h"

/** Opaque pipeline handle. */
typedef struct OpadPipeline OpadPipeline;

/**
 * Create a fully initialized pipeline.
 * @return Heap-allocated pipeline, or NULL on allocation failure.
 */
OpadPipeline* opad_pipeline_create(const OpadConfig* config);

/** Free the pipeline and all owned sub-modules. Safe with NULL. */
void opad_pipeline_destroy(OpadPipeline* p);

/** Reset all state for a new session (keeps the config). */
void opad_pipeline_reset(OpadPipeline* p);

/**
 * Analyze one frame end-to-end.
 *
 * @param p   The pipeline.
 * @param in  Deserialized per-frame input.
 * @param out Filled with all analysis results and stabilized status.
 */
void opad_pipeline_analyze_frame(OpadPipeline* p,
                                  const OpadFrameInput* in,
                                  OpadFrameOutput* out);

/** Externally advance the challenge to LIVE. */
void opad_pipeline_challenge_advance_to_live(OpadPipeline* p);

/** Externally advance the challenge to DONE. */
void opad_pipeline_challenge_advance_to_done(OpadPipeline* p);

/**
 * Handle a spoof in the challenge.
 * @return true if max attempts reached.
 */
bool opad_pipeline_challenge_handle_spoof(OpadPipeline* p);

#endif /* OPENPAD_PIPELINE_H */
