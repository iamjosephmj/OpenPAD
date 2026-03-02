/**
 * Unit tests for opad_classify, opad_compute_aggregate_score, opad_stabilizer_*.
 */

extern "C" {
#include <openpad/aggregation.h>
#include <openpad/config.h>
#include <openpad/types.h>
}

#include <gtest/gtest.h>

TEST(Aggregation, ClassifyNullTemporalReturnsAnalyzing) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadPadStatus status = opad_classify(&cfg, NULL, NULL, NULL, NULL, NULL, NULL);
    EXPECT_EQ(OPAD_STATUS_ANALYZING, status);
}

TEST(Aggregation, ClassifyInsufficientFramesReturnsAnalyzing) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTemporalFeatures temporal = {};
    temporal.face_detected = true;
    temporal.face_confidence = 0.9f;
    temporal.frames_collected = 1;
    temporal.consecutive_face_frames = 5;

    OpadPadStatus status = opad_classify(&cfg, NULL, NULL, NULL, NULL, NULL, &temporal);
    EXPECT_EQ(OPAD_STATUS_ANALYZING, status);
}

TEST(Aggregation, ClassifyNoFaceReturnsNoFace) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTemporalFeatures temporal = {};
    temporal.face_detected = false;
    temporal.frames_collected = 5;
    temporal.consecutive_face_frames = 5;

    OpadPadStatus status = opad_classify(&cfg, NULL, NULL, NULL, NULL, NULL, &temporal);
    EXPECT_EQ(OPAD_STATUS_NO_FACE, status);
}

TEST(Aggregation, ClassifyDeviceGateTriggersSpoof) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTemporalFeatures temporal = {};
    temporal.face_detected = true;
    temporal.face_confidence = 0.9f;
    temporal.frames_collected = 5;
    temporal.consecutive_face_frames = 5;

    OpadDeviceInput device = {};
    device.device_detected = true;
    device.overlap_with_face = true;
    device.max_confidence = 0.9f;

    OpadPadStatus status = opad_classify(&cfg, NULL, NULL, NULL, &device, NULL, &temporal);
    EXPECT_EQ(OPAD_STATUS_SPOOF_SUSPECTED, status);
}

TEST(Aggregation, ClassifyTextureGateTriggersSpoof) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTemporalFeatures temporal = {};
    temporal.face_detected = true;
    temporal.face_confidence = 0.9f;
    temporal.frames_collected = 5;
    temporal.consecutive_face_frames = 5;

    OpadTextureInput texture = {};
    texture.genuine_score = 0.2f;

    OpadPadStatus status = opad_classify(&cfg, &texture, NULL, NULL, NULL, NULL, &temporal);
    EXPECT_EQ(OPAD_STATUS_SPOOF_SUSPECTED, status);
}

TEST(Aggregation, ClassifyAllPassReturnsLive) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTemporalFeatures temporal = {};
    temporal.face_detected = true;
    temporal.face_confidence = 0.9f;
    temporal.frames_collected = 5;
    temporal.consecutive_face_frames = 5;

    OpadTextureInput texture = {};
    texture.genuine_score = 0.8f;

    OpadPadStatus status = opad_classify(&cfg, &texture, NULL, NULL, NULL, NULL, &temporal);
    EXPECT_EQ(OPAD_STATUS_LIVE, status);
}

TEST(Aggregation, ComputeAggregateScoreWithCdcn) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    OpadTextureInput texture = {};
    texture.genuine_score = 1.0f;

    OpadDepthInput depth = {};
    depth.has_mn3 = true;
    depth.mn3_real_score = 1.0f;
    depth.has_cdcn = true;
    depth.cdcn_depth_score = 1.0f;

    OpadDeviceInput device = {};
    device.device_detected = false;
    device.spoof_score = 0.0f;

    float score = opad_compute_aggregate_score(&cfg, &texture, &depth, &device);
    EXPECT_GE(score, 0.99f);
    EXPECT_LE(score, 1.01f);
}

TEST(Stabilizer, CreateAndDestroy) {
    OpadStateStabilizer* s = opad_stabilizer_create(OPAD_STATUS_ANALYZING);
    ASSERT_NE(nullptr, s);
    opad_stabilizer_destroy(s);
}

TEST(Stabilizer, NoFaceTransitionsImmediately) {
    OpadStateStabilizer* s = opad_stabilizer_create(OPAD_STATUS_ANALYZING);
    ASSERT_NE(nullptr, s);

    OpadPadStatus status = opad_stabilizer_update(s, OPAD_STATUS_NO_FACE, 5, 8);
    EXPECT_EQ(OPAD_STATUS_NO_FACE, status);

    opad_stabilizer_destroy(s);
}

TEST(Stabilizer, LiveRequiresExitConsecutive) {
    OpadStateStabilizer* s = opad_stabilizer_create(OPAD_STATUS_NO_FACE);
    ASSERT_NE(nullptr, s);

    for (int i = 0; i < 7; i++) {
        opad_stabilizer_update(s, OPAD_STATUS_LIVE, 5, 8);
    }
    OpadPadStatus status = opad_stabilizer_update(s, OPAD_STATUS_LIVE, 5, 8);
    EXPECT_EQ(OPAD_STATUS_LIVE, status);

    opad_stabilizer_destroy(s);
}

TEST(Stabilizer, Reset) {
    OpadStateStabilizer* s = opad_stabilizer_create(OPAD_STATUS_NO_FACE);
    ASSERT_NE(nullptr, s);

    opad_stabilizer_update(s, OPAD_STATUS_NO_FACE, 5, 8);
    opad_stabilizer_reset(s);

    OpadPadStatus status = opad_stabilizer_update(s, OPAD_STATUS_LIVE, 2, 2);
    EXPECT_EQ(OPAD_STATUS_ANALYZING, status);

    opad_stabilizer_destroy(s);
}
