/**
 * @file config.c
 * @brief Pipeline configuration: defaults and wire-format parsing.
 *
 * Wire format (172 bytes, little-endian):
 *   Offset   0: 21 x float32 — thresholds and weights
 *   Offset  84: 44 bytes padding (zeros)
 *   Offset 128: 11 x int32   — frame counts and window sizes
 */

#include <openpad/config.h>
#include <string.h>

static float read_f32_le(const uint8_t* p) {
    float v;
    memcpy(&v, p, 4);
    return v;
}

static int32_t read_i32_le(const uint8_t* p) {
    int32_t v;
    memcpy(&v, p, 4);
    return v;
}

void opad_config_default(OpadConfig* out) {
    out->min_face_confidence            = 0.55f;
    out->texture_genuine_threshold      = 0.5f;
    out->positioning_min_face_area      = 0.04f;
    out->positioning_max_face_area      = 0.45f;
    out->positioning_center_tolerance   = 0.08f;
    out->challenge_closer_min_increase  = 0.15f;
    out->challenge_center_tolerance     = 0.20f;
    out->mn3_gate_threshold             = 0.20f;
    out->depth_flatness_threshold       = 0.4f;
    out->device_confidence_threshold    = 0.5f;
    out->moire_threshold                = 0.6f;
    out->lbp_screen_threshold           = 0.7f;
    out->photometric_min_score          = 0.30f;
    out->texture_weight                 = 0.15f;
    out->mn3_weight                     = 0.20f;
    out->cdcn_weight                    = 0.55f;
    out->device_weight                  = 0.10f;
    out->genuine_probability_threshold  = 0.70f;
    out->spoof_attempt_penalty_per_count = 0.08f;
    out->max_genuine_probability_threshold = 0.85f;
    out->face_consistency_threshold     = 0.70f;
    out->min_frames_for_decision        = 3;
    out->sliding_window_size            = 15;
    out->min_consecutive_face_frames    = 3;
    out->enter_consecutive              = 2;
    out->exit_consecutive               = 3;
    out->positioning_stable_frames      = 5;
    out->challenge_stable_frames        = 2;
    out->challenge_min_frames           = 3;
    out->analyzing_stable_frames        = 2;
    out->max_spoof_attempts             = 3;
    out->max_fps                        = 8;
}

bool opad_config_parse(const uint8_t* bytes, size_t len, OpadConfig* out) {
    if (len < 172) {
        opad_config_default(out);
        return false;
    }

    /* 21 floats at bytes 0-83 */
    out->min_face_confidence            = read_f32_le(bytes +  0);
    out->texture_genuine_threshold      = read_f32_le(bytes +  4);
    out->positioning_min_face_area      = read_f32_le(bytes +  8);
    out->positioning_max_face_area      = read_f32_le(bytes + 12);
    out->positioning_center_tolerance   = read_f32_le(bytes + 16);
    out->challenge_closer_min_increase  = read_f32_le(bytes + 20);
    out->challenge_center_tolerance     = read_f32_le(bytes + 24);
    out->mn3_gate_threshold             = read_f32_le(bytes + 28);
    out->depth_flatness_threshold       = read_f32_le(bytes + 32);
    out->device_confidence_threshold    = read_f32_le(bytes + 36);
    out->moire_threshold                = read_f32_le(bytes + 40);
    out->lbp_screen_threshold           = read_f32_le(bytes + 44);
    out->photometric_min_score          = read_f32_le(bytes + 48);
    out->texture_weight                 = read_f32_le(bytes + 52);
    out->mn3_weight                     = read_f32_le(bytes + 56);
    out->cdcn_weight                    = read_f32_le(bytes + 60);
    out->device_weight                  = read_f32_le(bytes + 64);
    out->genuine_probability_threshold  = read_f32_le(bytes + 68);
    out->spoof_attempt_penalty_per_count = read_f32_le(bytes + 72);
    out->max_genuine_probability_threshold = read_f32_le(bytes + 76);
    out->face_consistency_threshold     = read_f32_le(bytes + 80);

    /* 11 int32s at bytes 128-171 (bytes 84-127 are padding) */
    out->min_frames_for_decision        = read_i32_le(bytes + 128);
    out->sliding_window_size            = read_i32_le(bytes + 132);
    out->min_consecutive_face_frames    = read_i32_le(bytes + 136);
    out->enter_consecutive              = read_i32_le(bytes + 140);
    out->exit_consecutive               = read_i32_le(bytes + 144);
    out->positioning_stable_frames      = read_i32_le(bytes + 148);
    out->challenge_stable_frames        = read_i32_le(bytes + 152);
    out->challenge_min_frames           = read_i32_le(bytes + 156);
    out->analyzing_stable_frames        = read_i32_le(bytes + 160);
    out->max_spoof_attempts             = read_i32_le(bytes + 164);
    out->max_fps                        = read_i32_le(bytes + 168);
    return true;
}
