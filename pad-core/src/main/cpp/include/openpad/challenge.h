/**
 * @file challenge.h
 * @brief Challenge-response state machine for liveness verification.
 *
 * Implements a "move closer" challenge that requires the user to increase
 * their face area relative to a calibrated baseline while staying centered.
 *
 * ### Phase flow:
 * ```
 * IDLE → ANALYZING → CHALLENGE_CLOSER → EVALUATING → LIVE → DONE
 * ```
 *
 * - **ANALYZING**: Wait for `analyzing_stable_frames` consecutive face frames.
 * - **CHALLENGE_CLOSER**: Calibrate baseline area, then require area increase
 *   >= `challenge_closer_min_increase` while centered. Hold for
 *   `challenge_stable_frames` frames with at least `challenge_min_frames` total.
 * - **EVALUATING**: Kotlin performs final ML-score evaluation.
 * - **LIVE / DONE**: Terminal states set externally.
 *
 * Spoof attempts decrement with retry; after `max_spoof_attempts` the state
 * machine moves directly to DONE.
 */

#ifndef OPENPAD_CHALLENGE_H
#define OPENPAD_CHALLENGE_H

#include "types.h"

/** Input for one challenge frame. */
typedef struct {
    const OpadFaceDetection* face;  /**< NULL if no face detected. */
    OpadPadStatus pad_status;
    float texture_genuine_score;
    bool  has_mn3;
    float mn3_real_score;
    bool  has_cdcn;
    float cdcn_depth_score;
} OpadChallengeFrameInput;

/** Opaque challenge state machine handle. */
typedef struct OpadMovementChallenge OpadMovementChallenge;

/** Create a challenge state machine initialized to IDLE. */
OpadMovementChallenge* opad_challenge_create(const OpadConfig* config);

/** Free the challenge state machine. */
void opad_challenge_destroy(OpadMovementChallenge* mc);

/**
 * Process one frame through the challenge state machine.
 *
 * @param mc     The challenge handle.
 * @param input  Frame-level inputs (face geometry + PAD status).
 * @param out    Filled with current phase, checkpoint flags, metrics.
 */
void opad_challenge_on_frame(OpadMovementChallenge* mc,
                              const OpadChallengeFrameInput* input,
                              OpadChallengeOutput* out);

/** Externally advance to LIVE (evaluation passed). */
void opad_challenge_advance_to_live(OpadMovementChallenge* mc);

/** Externally advance to DONE (session complete). */
void opad_challenge_advance_to_done(OpadMovementChallenge* mc);

/**
 * Handle a spoof attempt. Resets the challenge for retry.
 * @return true if max attempts reached (challenge moves to DONE).
 */
bool opad_challenge_handle_spoof(OpadMovementChallenge* mc);

/** Full reset to IDLE (new session). */
void opad_challenge_reset(OpadMovementChallenge* mc);

#endif /* OPENPAD_CHALLENGE_H */
