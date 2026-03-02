/**
 * @file classifier.c
 * @brief Per-frame PAD classifier and aggregate score computation.
 *
 * ### Classification Logic (opad_classify)
 * Sequential short-circuit gates — the first gate that fires determines
 * the status. Order matters: cheaper/faster checks run first.
 *
 *   1. Not enough frames or face frames → ANALYZING (need more data).
 *   2. Device detected overlapping face  → SPOOF (phone/tablet in frame).
 *   3. Moiré AND LBP both high          → SPOOF (screen artifacts).
 *   4. Texture below threshold           → SPOOF (MiniFASNet says spoof).
 *   5. CDCN depth below threshold        → SPOOF (flat depth map).
 *   6. Photometric below threshold       → SPOOF (unnatural lighting).
 *   7. Texture passes                    → LIVE.
 *
 * ### Aggregate Score (opad_compute_aggregate_score)
 * Weighted combination of texture, MN3, CDCN, and device scores.
 * When CDCN is unavailable, its weight is redistributed 2:1 between
 * texture and MN3.
 */

#include <openpad/aggregation.h>

OpadPadStatus opad_classify(const OpadConfig* config,
                             const OpadTextureInput* texture,
                             const OpadDepthInput* depth,
                             const OpadFrequencyInput* frequency,
                             const OpadDeviceInput* device,
                             const OpadPhotometricInput* photometric,
                             const OpadTemporalFeatures* temporal) {
    if (!temporal) return OPAD_STATUS_ANALYZING;
    if (temporal->frames_collected < config->min_frames_for_decision)
        return OPAD_STATUS_ANALYZING;
    if (!temporal->face_detected) return OPAD_STATUS_NO_FACE;
    if (temporal->face_confidence < config->min_face_confidence)
        return OPAD_STATUS_NO_FACE;
    if (temporal->consecutive_face_frames < config->min_consecutive_face_frames)
        return OPAD_STATUS_ANALYZING;

    if (device && device->device_detected && device->overlap_with_face
        && device->max_confidence >= config->device_confidence_threshold)
        return OPAD_STATUS_SPOOF_SUSPECTED;

    if (frequency) {
        bool moire_flag = frequency->moire_score >= config->moire_threshold;
        bool lbp_flag   = frequency->lbp_screen_score >= config->lbp_screen_threshold;
        if (moire_flag && lbp_flag)
            return OPAD_STATUS_SPOOF_SUSPECTED;
    }

    float texture_genuine = texture ? texture->genuine_score : 0.5f;
    bool has_texture      = (texture != NULL);
    bool texture_passes   = texture_genuine >= config->texture_genuine_threshold;
    if (has_texture && !texture_passes)
        return OPAD_STATUS_SPOOF_SUSPECTED;

    if (depth && depth->has_cdcn) {
        if (depth->cdcn_depth_score < config->depth_flatness_threshold)
            return OPAD_STATUS_SPOOF_SUSPECTED;
    }

    if (photometric) {
        if (photometric->combined_score < config->photometric_min_score)
            return OPAD_STATUS_SPOOF_SUSPECTED;
    }

    if (texture_passes) return OPAD_STATUS_LIVE;

    return OPAD_STATUS_ANALYZING;
}

float opad_compute_aggregate_score(const OpadConfig* config,
                                    const OpadTextureInput* texture,
                                    const OpadDepthInput* depth,
                                    const OpadDeviceInput* device) {
    float tex = texture ? texture->genuine_score : 0.5f;
    float mn3 = (depth && depth->has_mn3) ? depth->mn3_real_score : 0.5f;
    float dev = 1.0f - (device ? device->spoof_score : 0.0f);

    if (depth && depth->has_cdcn) {
        float cdcn  = depth->cdcn_depth_score;
        float total = config->texture_weight + config->mn3_weight
                    + config->cdcn_weight + config->device_weight;
        return (tex  * config->texture_weight
              + mn3  * config->mn3_weight
              + cdcn * config->cdcn_weight
              + dev  * config->device_weight) / total;
    } else {
        float cdcn_redist = config->cdcn_weight;
        float eff_tex = config->texture_weight + cdcn_redist * 2.0f / 3.0f;
        float eff_mn3 = config->mn3_weight     + cdcn_redist / 3.0f;
        float total   = eff_tex + eff_mn3 + config->device_weight;
        return (tex * eff_tex + mn3 * eff_mn3 + dev * config->device_weight) / total;
    }
}
