/**
 * Unit tests for opad_temporal_tracker_* functions.
 */

extern "C" {
#include <openpad/temporal.h>
#include <openpad/config.h>
#include <openpad/types.h>
}

#include <cstring>
#include <gtest/gtest.h>

class TemporalTrackerTest : public ::testing::Test {
protected:
    OpadConfig cfg;
    OpadTemporalTracker* tracker = nullptr;

    void SetUp() override {
        opad_config_default(&cfg);
        tracker = opad_temporal_tracker_create(&cfg);
        ASSERT_NE(nullptr, tracker);
    }

    void TearDown() override {
        opad_temporal_tracker_destroy(tracker);
    }
};

TEST_F(TemporalTrackerTest, CreateAndDestroyLifecycle) {
    // SetUp/TearDown handles this — test just verifies no crash
    SUCCEED();
}

TEST_F(TemporalTrackerTest, UpdateWithNoFaceReturnsFaceNotDetected) {
    OpadTemporalFeatures out;
    std::memset(&out, 0, sizeof(out));

    opad_temporal_tracker_update(tracker, NULL, 0.9f, &out);

    EXPECT_FALSE(out.face_detected);
    EXPECT_EQ(0, out.consecutive_face_frames);
    EXPECT_EQ(1, out.frames_collected);
}

TEST_F(TemporalTrackerTest, UpdateWithFaceIncreasesConsecutiveCount) {
    OpadFaceDetection det = {0.5f, 0.5f, 0.1f, 0.9f};
    OpadTemporalFeatures out;

    for (int i = 0; i < 3; i++) {
        std::memset(&out, 0, sizeof(out));
        opad_temporal_tracker_update(tracker, &det, 0.9f, &out);
    }

    EXPECT_TRUE(out.face_detected);
    EXPECT_EQ(3, out.consecutive_face_frames);
}

TEST_F(TemporalTrackerTest, FramesCollectedIncrements) {
    OpadFaceDetection det = {0.5f, 0.5f, 0.1f, 0.9f};
    OpadTemporalFeatures out;

    opad_temporal_tracker_update(tracker, &det, 0.9f, &out);
    opad_temporal_tracker_update(tracker, NULL, 0.9f, &out);
    opad_temporal_tracker_update(tracker, &det, 0.9f, &out);

    EXPECT_EQ(3, out.frames_collected);
}

TEST_F(TemporalTrackerTest, ResetClearsState) {
    OpadFaceDetection det = {0.5f, 0.5f, 0.1f, 0.9f};
    OpadTemporalFeatures out;

    for (int i = 0; i < 5; i++) {
        opad_temporal_tracker_update(tracker, &det, 0.9f, &out);
    }
    EXPECT_EQ(5, out.frames_collected);

    opad_temporal_tracker_reset(tracker);

    std::memset(&out, 0, sizeof(out));
    opad_temporal_tracker_update(tracker, NULL, 0.9f, &out);
    EXPECT_EQ(1, out.frames_collected);
    EXPECT_EQ(0, out.consecutive_face_frames);
}

TEST_F(TemporalTrackerTest, NullDetectionResetsConsecutiveFaceFrames) {
    OpadFaceDetection det = {0.5f, 0.5f, 0.1f, 0.9f};
    OpadTemporalFeatures out;

    opad_temporal_tracker_update(tracker, &det, 0.9f, &out);
    opad_temporal_tracker_update(tracker, &det, 0.9f, &out);
    EXPECT_EQ(2, out.consecutive_face_frames);

    opad_temporal_tracker_update(tracker, NULL, 0.9f, &out);
    EXPECT_EQ(0, out.consecutive_face_frames);
}
