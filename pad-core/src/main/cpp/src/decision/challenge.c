/**
 * @file challenge.c
 * @brief "Move closer" challenge-response state machine.
 *
 * Flow:
 *   IDLE → ANALYZING → CHALLENGE_CLOSER → EVALUATING → LIVE → DONE
 *
 * ### ANALYZING
 * Counts face-detected frames. After `analyzing_stable_frames` consecutive
 * face frames, transitions to CHALLENGE_CLOSER.
 *
 * ### CHALLENGE_CLOSER
 * 1. **Baseline calibration**: averages face area over 2 frames.
 *    Fires `capture_checkpoint_1` when baseline is locked.
 * 2. **Hold tracking**: requires the user to move closer (area increase
 *    >= `challenge_closer_min_increase`) while staying centered (within
 *    `challenge_center_tolerance` of frame center).
 * 3. Hold frames accumulate when centered+closer; decrement by 2 otherwise
 *    (tolerates brief jitter).
 * 4. Once `challenge_stable_frames` hold frames AND `challenge_min_frames`
 *    total frames are reached, fires `capture_checkpoint_2` and transitions
 *    to EVALUATING.
 *
 * ### Spoof handling
 * On spoof, resets to ANALYZING with a retry counter. After
 * `max_spoof_attempts`, moves to DONE (terminal failure).
 */

#include <openpad/challenge.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define BASELINE_CALIBRATION_FRAMES 2

struct OpadMovementChallenge {
    OpadConfig         config;
    OpadChallengePhase phase;
    float              baseline_area;
    int32_t            analyzing_live_frames;
    int32_t            challenge_hold_frames;
    int32_t            spoof_attempt_count;
    bool               baseline_calibrated;
    float              baseline_areas[BASELINE_CALIBRATION_FRAMES];
    size_t             baseline_count;
    int32_t            total_frames;
    int32_t            hold_frames;
    float              max_area_increase;
    bool               completed;
};

OpadMovementChallenge* opad_challenge_create(const OpadConfig* config) {
    OpadMovementChallenge* mc = (OpadMovementChallenge*)calloc(1, sizeof(*mc));
    if (!mc) return NULL;
    mc->config = *config;
    mc->phase  = OPAD_PHASE_IDLE;
    return mc;
}

void opad_challenge_destroy(OpadMovementChallenge* mc) { free(mc); }

static void fill_output(const OpadMovementChallenge* mc,
                         bool cp1, bool cp2, OpadChallengeOutput* out) {
    out->phase                = mc->phase;
    out->capture_checkpoint_1 = cp1;
    out->capture_checkpoint_2 = cp2;
    out->baseline_area        = mc->baseline_area;
    out->total_frames         = mc->total_frames;
    out->hold_frames          = mc->hold_frames;
    out->max_area_increase    = mc->max_area_increase;
    out->completed            = mc->completed;
}

void opad_challenge_on_frame(OpadMovementChallenge* mc,
                              const OpadChallengeFrameInput* input,
                              OpadChallengeOutput* out) {
    bool cp1 = false, cp2 = false;

    switch (mc->phase) {
    case OPAD_PHASE_IDLE:
        mc->phase = OPAD_PHASE_ANALYZING;
        break;

    case OPAD_PHASE_ANALYZING:
        if (!input->face) {
            mc->analyzing_live_frames = 0;
            fill_output(mc, cp1, cp2, out);
            return;
        }
        mc->analyzing_live_frames++;
        if (mc->analyzing_live_frames >= mc->config.analyzing_stable_frames) {
            mc->baseline_calibrated = false;
            mc->baseline_count      = 0;
            mc->phase = OPAD_PHASE_CHALLENGE_CLOSER;
        }
        break;

    case OPAD_PHASE_POSITIONING:
        mc->phase = OPAD_PHASE_CHALLENGE_CLOSER;
        break;

    case OPAD_PHASE_CHALLENGE_CLOSER: {
        if (!input->face || input->pad_status == OPAD_STATUS_NO_FACE) {
            mc->challenge_hold_frames = 0;
            fill_output(mc, cp1, cp2, out);
            return;
        }

        float area = input->face->area;

        if (!mc->baseline_calibrated) {
            if (mc->baseline_count < BASELINE_CALIBRATION_FRAMES)
                mc->baseline_areas[mc->baseline_count++] = area;
            if (mc->baseline_count >= BASELINE_CALIBRATION_FRAMES) {
                float sum = 0;
                for (size_t i = 0; i < mc->baseline_count; i++)
                    sum += mc->baseline_areas[i];
                mc->baseline_area       = sum / (float)mc->baseline_count;
                mc->baseline_calibrated = true;
                cp1 = true;
            }
            fill_output(mc, cp1, cp2, out);
            return;
        }

        float area_increase = mc->baseline_area > 0.0f
            ? (area - mc->baseline_area) / mc->baseline_area : 0.0f;
        if (area_increase > mc->max_area_increase)
            mc->max_area_increase = area_increase;
        mc->total_frames++;

        float cx  = input->face->center_x;
        float cy  = input->face->center_y;
        float tol = mc->config.challenge_center_tolerance;
        bool centered = fabsf(cx - 0.5f) <= tol && fabsf(cy - 0.5f) <= tol;
        bool closer   = area_increase >= mc->config.challenge_closer_min_increase;

        if (closer && centered) {
            mc->challenge_hold_frames++;
            mc->hold_frames = mc->challenge_hold_frames;

            if (mc->challenge_hold_frames >= mc->config.challenge_stable_frames
                && mc->total_frames >= mc->config.challenge_min_frames) {
                cp2 = true;
                mc->completed = true;
                mc->phase     = OPAD_PHASE_EVALUATING;
            }
        } else {
            mc->challenge_hold_frames -= 2;
            if (mc->challenge_hold_frames < 0) mc->challenge_hold_frames = 0;
        }
        break;
    }
    case OPAD_PHASE_EVALUATING:
    case OPAD_PHASE_LIVE:
    case OPAD_PHASE_DONE:
        break;
    }

    fill_output(mc, cp1, cp2, out);
}

void opad_challenge_advance_to_live(OpadMovementChallenge* mc) {
    mc->phase = OPAD_PHASE_LIVE;
}

void opad_challenge_advance_to_done(OpadMovementChallenge* mc) {
    mc->phase = OPAD_PHASE_DONE;
}

bool opad_challenge_handle_spoof(OpadMovementChallenge* mc) {
    mc->spoof_attempt_count++;
    if (mc->config.max_spoof_attempts > 0
        && mc->spoof_attempt_count >= mc->config.max_spoof_attempts) {
        mc->phase = OPAD_PHASE_DONE;
        return true;
    }
    mc->phase                 = OPAD_PHASE_ANALYZING;
    mc->analyzing_live_frames = 0;
    mc->challenge_hold_frames = 0;
    mc->baseline_calibrated   = false;
    mc->baseline_count        = 0;
    mc->total_frames          = 0;
    mc->hold_frames           = 0;
    mc->max_area_increase     = 0.0f;
    mc->completed             = false;
    return false;
}

void opad_challenge_reset(OpadMovementChallenge* mc) {
    mc->phase                 = OPAD_PHASE_IDLE;
    mc->baseline_area         = 0.0f;
    mc->baseline_calibrated   = false;
    mc->baseline_count        = 0;
    mc->analyzing_live_frames = 0;
    mc->challenge_hold_frames = 0;
    mc->spoof_attempt_count   = 0;
    mc->total_frames          = 0;
    mc->hold_frames           = 0;
    mc->max_area_increase     = 0.0f;
    mc->completed             = false;
}
