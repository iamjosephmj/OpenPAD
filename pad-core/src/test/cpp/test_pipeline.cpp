/**
 * Unit tests for opad_pipeline_* functions.
 */

extern "C" {
#include <openpad/pipeline.h>
#include <openpad/config.h>
#include <openpad/types.h>
}

#include <cstring>
#include <gtest/gtest.h>

class PipelineTest : public ::testing::Test {
protected:
    OpadConfig cfg;
    OpadPipeline* pipeline = nullptr;

    void SetUp() override {
        opad_config_default(&cfg);
        pipeline = opad_pipeline_create(&cfg);
        ASSERT_NE(nullptr, pipeline);
    }

    void TearDown() override {
        opad_pipeline_destroy(pipeline);
    }

    OpadFrameInput makeEmptyInput() {
        OpadFrameInput input;
        std::memset(&input, 0, sizeof(input));
        input.has_face = false;
        return input;
    }
};

TEST_F(PipelineTest, CreateAndDestroyLifecycle) {
    SUCCEED();
}

TEST_F(PipelineTest, ResetDoesNotCrash) {
    opad_pipeline_reset(pipeline);
    SUCCEED();
}

TEST_F(PipelineTest, AnalyzeFrameNoFaceReturnsAnalyzingOrNoFace) {
    OpadFrameInput input = makeEmptyInput();
    OpadFrameOutput output;
    std::memset(&output, 0, sizeof(output));

    opad_pipeline_analyze_frame(pipeline, &input, &output);

    // With no face and fresh pipeline, expect ANALYZING or NO_FACE
    EXPECT_TRUE(
        output.pad_status == OPAD_STATUS_ANALYZING ||
        output.pad_status == OPAD_STATUS_NO_FACE
    );
}

TEST_F(PipelineTest, AnalyzeFrameOutputFieldsValid) {
    OpadFrameInput input = makeEmptyInput();
    // Fill downsampled frame with uniform gray
    for (int i = 0; i < OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE; i++) {
        input.frame_downsampled[i] = 0.5f;
    }

    OpadFrameOutput output;
    std::memset(&output, 0, sizeof(output));
    opad_pipeline_analyze_frame(pipeline, &input, &output);

    EXPECT_GE(output.aggregated_score, 0.0f);
    EXPECT_LE(output.aggregated_score, 1.0f);
    EXPECT_GE(output.frame_similarity, 0.0f);
    EXPECT_LE(output.frame_similarity, 1.0f);
}

TEST_F(PipelineTest, ChallengeAdvanceToLiveDoesNotCrash) {
    opad_pipeline_challenge_advance_to_live(pipeline);
    SUCCEED();
}

TEST_F(PipelineTest, ChallengeAdvanceToDoneDoesNotCrash) {
    opad_pipeline_challenge_advance_to_done(pipeline);
    SUCCEED();
}

TEST_F(PipelineTest, ChallengeHandleSpoofReturnsFalseInitially) {
    bool maxReached = opad_pipeline_challenge_handle_spoof(pipeline);
    EXPECT_FALSE(maxReached);
}

TEST_F(PipelineTest, MultipleFramesIncrementTemporalState) {
    OpadFrameInput input = makeEmptyInput();
    input.has_face = true;
    OpadFaceDetection det = {0.5f, 0.5f, 0.1f, 0.9f};
    input.center_x = det.center_x;
    input.center_y = det.center_y;
    input.area = det.area;
    input.confidence = det.confidence;

    for (int i = 0; i < OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE; i++) {
        input.frame_downsampled[i] = 0.5f;
    }
    for (int i = 0; i < OPAD_FFT_SIZE * OPAD_FFT_SIZE; i++) {
        input.face_crop_64_gray[i] = 0.5f;
    }

    OpadFrameOutput out1, out2;
    std::memset(&out1, 0, sizeof(out1));
    std::memset(&out2, 0, sizeof(out2));

    opad_pipeline_analyze_frame(pipeline, &input, &out1);
    opad_pipeline_analyze_frame(pipeline, &input, &out2);

    EXPECT_EQ(2, out2.temporal.frames_collected);
    EXPECT_TRUE(out2.temporal.face_detected);
}
