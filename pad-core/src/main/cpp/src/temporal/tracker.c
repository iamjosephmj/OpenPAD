/**
 * @file tracker.c
 * @brief Temporal feature tracker using ring buffers.
 *
 * Tracks face center (x, y), area, and confidence over a sliding window.
 * Computes:
 *   - Movement variance: sum of center_x and center_y variance × 10000.
 *   - Blink detection: confidence dip-and-recovery or area dip-and-recovery
 *     within a 12-frame window (set-once — once detected, stays true).
 *   - Movement smoothness: inverse of average acceleration (second derivative
 *     of center trajectory), scaled by 5000.
 *   - Face size stability: sqrt of area variance.
 */

#include <openpad/temporal.h>
#include "ringbuf.h"
#include <stdlib.h>
#include <math.h>
#include <string.h>

#define BLINK_WINDOW         12
#define BLINK_MIN_BASELINE   0.80f
#define BLINK_CONFIDENCE_DIP 0.025f
#define AREA_DIP_THRESHOLD   0.95f
#define AREA_RECOVERY        0.98f
#define SMOOTHNESS_SCALE     5000.0f

struct OpadTemporalTracker {
    OpadConfig  config;
    OpadRingBuf center_x;
    OpadRingBuf center_y;
    OpadRingBuf area;
    OpadRingBuf confidence;
    int32_t     frames_collected;
    int32_t     consecutive_face_frames;
    bool        blink_ever_detected;
};

OpadTemporalTracker* opad_temporal_tracker_create(const OpadConfig* config) {
    OpadTemporalTracker* t = (OpadTemporalTracker*)calloc(1, sizeof(*t));
    if (!t) return NULL;
    t->config = *config;
    size_t win = (size_t)config->sliding_window_size;
    opad_ring_init(&t->center_x,   win);
    opad_ring_init(&t->center_y,   win);
    opad_ring_init(&t->area,       win);
    opad_ring_init(&t->confidence, win);
    return t;
}

void opad_temporal_tracker_destroy(OpadTemporalTracker* t) {
    free(t);
}

static float compute_movement_variance(const OpadTemporalTracker* t) {
    if (t->center_x.count < 2) return 0.0f;
    float vx = opad_ring_variance(&t->center_x);
    float vy = opad_ring_variance(&t->center_y);
    return (vx + vy) * 10000.0f;
}

static bool detect_blink(OpadTemporalTracker* t) {
    if (t->blink_ever_detected) return true;

    /* Confidence-based: baseline from first 3, look for dip + recovery */
    if (t->confidence.count >= BLINK_WINDOW) {
        size_t start = t->confidence.count - BLINK_WINDOW;
        float baseline = 0.0f;
        for (size_t i = 0; i < 3; i++)
            baseline += opad_ring_get(&t->confidence, start + i);
        baseline /= 3.0f;
        if (baseline > BLINK_MIN_BASELINE) {
            bool found_dip = false;
            for (size_t i = 3; i < BLINK_WINDOW; i++) {
                float conf = opad_ring_get(&t->confidence, start + i);
                float drop = baseline - conf;
                if (!found_dip && drop >= BLINK_CONFIDENCE_DIP)
                    found_dip = true;
                else if (found_dip && drop < BLINK_CONFIDENCE_DIP * 0.3f) {
                    t->blink_ever_detected = true;
                    return true;
                }
            }
        }
    }

    /* Area-based: look for dip below 95% of mean, then recovery above 98% */
    if (t->area.count >= BLINK_WINDOW) {
        size_t start = t->area.count - BLINK_WINDOW;
        float sum = 0.0f;
        for (size_t i = 0; i < BLINK_WINDOW; i++)
            sum += opad_ring_get(&t->area, start + i);
        float mean = sum / (float)BLINK_WINDOW;
        if (mean > 0.0f) {
            bool found_dip = false;
            for (size_t i = 0; i < BLINK_WINDOW; i++) {
                float a = opad_ring_get(&t->area, start + i);
                if (!found_dip && a < mean * AREA_DIP_THRESHOLD)
                    found_dip = true;
                else if (found_dip && a > mean * AREA_RECOVERY) {
                    t->blink_ever_detected = true;
                    return true;
                }
            }
        }
    }

    return false;
}

static float compute_smoothness(const OpadTemporalTracker* t) {
    if (t->center_x.count < 3) return 1.0f;
    float total_accel = 0.0f;
    size_t count = 0;
    for (size_t i = 2; i < t->center_x.count; i++) {
        float ax = opad_ring_get(&t->center_x, i)
                   - 2.0f * opad_ring_get(&t->center_x, i - 1)
                   + opad_ring_get(&t->center_x, i - 2);
        float ay = opad_ring_get(&t->center_y, i)
                   - 2.0f * opad_ring_get(&t->center_y, i - 1)
                   + opad_ring_get(&t->center_y, i - 2);
        total_accel += sqrtf(ax * ax + ay * ay);
        count++;
    }
    if (count == 0) return 1.0f;
    float avg = total_accel / (float)count;
    return 1.0f / (1.0f + avg * SMOOTHNESS_SCALE);
}

void opad_temporal_tracker_update(OpadTemporalTracker* t,
                                   const OpadFaceDetection* det,
                                   float frame_similarity,
                                   OpadTemporalFeatures* out) {
    t->frames_collected++;

    if (det) {
        t->consecutive_face_frames++;
        opad_ring_push(&t->center_x,   det->center_x);
        opad_ring_push(&t->center_y,   det->center_y);
        opad_ring_push(&t->area,       det->area);
        opad_ring_push(&t->confidence, det->confidence);

        out->face_detected          = true;
        out->face_confidence        = det->confidence;
        out->face_bbox_center_x     = det->center_x;
        out->face_bbox_center_y     = det->center_y;
        out->face_bbox_area         = det->area;
        out->head_movement_variance = compute_movement_variance(t);
        out->face_size_stability    = sqrtf(opad_ring_variance(&t->area));
        out->blink_detected         = detect_blink(t);
        out->frames_collected       = t->frames_collected;
        out->frame_similarity       = frame_similarity;
        out->consecutive_face_frames = t->consecutive_face_frames;
        out->movement_smoothness    = compute_smoothness(t);
    } else {
        t->consecutive_face_frames = 0;
        memset(out, 0, sizeof(*out));
        out->frames_collected = t->frames_collected;
        out->frame_similarity = frame_similarity;
    }
}

void opad_temporal_tracker_reset(OpadTemporalTracker* t) {
    opad_ring_clear(&t->center_x);
    opad_ring_clear(&t->center_y);
    opad_ring_clear(&t->area);
    opad_ring_clear(&t->confidence);
    t->frames_collected        = 0;
    t->consecutive_face_frames = 0;
    t->blink_ever_detected     = false;
}
