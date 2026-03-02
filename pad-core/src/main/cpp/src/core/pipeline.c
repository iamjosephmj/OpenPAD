/**
 * @file pipeline.c
 * @brief Top-level pipeline orchestrator.
 *
 * Owns all sub-modules and runs the per-frame analysis flow:
 * similarity → sharpness → FFT → LBP → photometric → temporal →
 * classify → stabilize → aggregate → challenge → output.
 */

#include <openpad/pipeline.h>
#include <openpad/config.h>
#include <openpad/image.h>
#include <openpad/frequency.h>
#include <openpad/photometric.h>
#include <openpad/temporal.h>
#include <openpad/aggregation.h>
#include <openpad/challenge.h>
#include <stdlib.h>
#include <string.h>

#define FRAME_DIM (OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE)

struct OpadPipeline {
    OpadConfig              config;
    OpadTemporalTracker*    tracker;
    OpadStateStabilizer*    stabilizer;
    OpadMovementChallenge*  challenge;
    float                   prev_frame[FRAME_DIM];
    bool                    has_prev_frame;
};

OpadPipeline* opad_pipeline_create(const OpadConfig* config) {
    OpadPipeline* p = (OpadPipeline*)calloc(1, sizeof(*p));
    if (!p) return NULL;
    p->config     = *config;
    p->tracker    = opad_temporal_tracker_create(config);
    p->stabilizer = opad_stabilizer_create(OPAD_STATUS_ANALYZING);
    p->challenge  = opad_challenge_create(config);
    p->has_prev_frame = false;
    if (!p->tracker || !p->stabilizer || !p->challenge) {
        opad_pipeline_destroy(p);
        return NULL;
    }
    return p;
}

void opad_pipeline_destroy(OpadPipeline* p) {
    if (!p) return;
    if (p->tracker)    opad_temporal_tracker_destroy(p->tracker);
    if (p->stabilizer) opad_stabilizer_destroy(p->stabilizer);
    if (p->challenge)  opad_challenge_destroy(p->challenge);
    free(p);
}

void opad_pipeline_reset(OpadPipeline* p) {
    if (!p) return;
    opad_temporal_tracker_reset(p->tracker);
    opad_stabilizer_reset(p->stabilizer);
    opad_challenge_reset(p->challenge);
    p->has_prev_frame = false;
}

void opad_pipeline_analyze_frame(OpadPipeline* p,
                                  const OpadFrameInput* in,
                                  OpadFrameOutput* out) {
    memset(out, 0, sizeof(*out));

    /* 1. Frame similarity */
    float similarity = opad_compute_frame_similarity(
        p->has_prev_frame ? p->prev_frame : NULL,
        in->frame_downsampled, FRAME_DIM);
    memcpy(p->prev_frame, in->frame_downsampled, sizeof(p->prev_frame));
    p->has_prev_frame = true;

    /* 2. Face sharpness */
    float sharpness, qvar;
    opad_compute_face_sharpness(in->face_crop_64_gray, OPAD_FFT_SIZE,
                                 &sharpness, &qvar);

    /* 3. Frequency analysis */
    OpadFrequencyResult freq;
    opad_fft_moire(in->face_crop_64_gray, &freq);

    OpadLbpResult lbp;
    opad_lbp_screen(in->face_crop_64_argb, &lbp);

    /* 4. Photometric analysis */
    OpadPhotometricResult photo;
    opad_photometric_analyze(in->face_crop_80_argb, &photo);

    /* 5. Temporal tracking */
    OpadFaceDetection det;
    const OpadFaceDetection* det_ptr = NULL;
    if (in->has_face) {
        det.center_x   = in->center_x;
        det.center_y   = in->center_y;
        det.area       = in->area;
        det.confidence = in->confidence;
        det_ptr = &det;
    }
    OpadTemporalFeatures temporal;
    opad_temporal_tracker_update(p->tracker, det_ptr, similarity, &temporal);

    /* 6. Classification + stabilization */
    OpadTextureInput tex     = { .genuine_score = in->texture_genuine };
    OpadDepthInput depth     = { .has_mn3 = in->has_mn3,
                                 .mn3_real_score = in->mn3_real,
                                 .has_cdcn = in->has_cdcn,
                                 .cdcn_depth_score = in->cdcn };
    OpadDeviceInput device   = { .device_detected = in->device_detected,
                                 .overlap_with_face = in->device_overlap,
                                 .max_confidence = in->device_max_conf,
                                 .spoof_score = in->device_spoof };
    OpadFrequencyInput fin   = { .moire_score = freq.moire_score,
                                 .lbp_screen_score = lbp.screen_score };
    OpadPhotometricInput pin = { .combined_score = photo.combined_score };

    OpadPadStatus candidate = opad_classify(&p->config,
        &tex, &depth, &fin, &device, &pin, &temporal);
    OpadPadStatus stable = opad_stabilizer_update(p->stabilizer, candidate,
        p->config.enter_consecutive, p->config.exit_consecutive);

    /* 7. Aggregate score */
    float agg_score = opad_compute_aggregate_score(&p->config,
        &tex, &depth, &device);

    /* 8. Challenge state machine */
    OpadChallengeFrameInput ch_in;
    ch_in.face                = det_ptr;
    ch_in.pad_status          = stable;
    ch_in.texture_genuine_score = in->texture_genuine;
    ch_in.has_mn3             = in->has_mn3;
    ch_in.mn3_real_score      = in->mn3_real;
    ch_in.has_cdcn            = in->has_cdcn;
    ch_in.cdcn_depth_score    = in->cdcn;

    OpadChallengeOutput ch_out;
    opad_challenge_on_frame(p->challenge, &ch_in, &ch_out);

    /* 9. Assemble output */
    out->pad_status       = stable;
    out->aggregated_score = agg_score;
    out->frame_similarity = similarity;
    out->face_sharpness   = sharpness;
    out->challenge        = ch_out;
    out->frequency        = freq;
    out->lbp              = lbp;
    out->photometric      = photo;
    out->temporal         = temporal;
}

void opad_pipeline_challenge_advance_to_live(OpadPipeline* p) {
    if (p) opad_challenge_advance_to_live(p->challenge);
}

void opad_pipeline_challenge_advance_to_done(OpadPipeline* p) {
    if (p) opad_challenge_advance_to_done(p->challenge);
}

bool opad_pipeline_challenge_handle_spoof(OpadPipeline* p) {
    return p ? opad_challenge_handle_spoof(p->challenge) : false;
}
