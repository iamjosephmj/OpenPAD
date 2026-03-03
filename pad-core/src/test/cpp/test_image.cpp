/**
 * Unit tests for opad_compute_frame_similarity and opad_compute_face_sharpness.
 */

extern "C" {
#include <openpad/image.h>
#include <openpad/types.h>
}

#include <cmath>
#include <cstring>
#include <gtest/gtest.h>

static const size_t SIM_LEN = OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE;
static const size_t SHARP_SIZE = OPAD_SHARPNESS_SIZE;
static const size_t SHARP_LEN = SHARP_SIZE * SHARP_SIZE;

TEST(Image, SimilarityIdenticalFramesReturnsOne) {
    float frame[SIM_LEN];
    for (size_t i = 0; i < SIM_LEN; i++) frame[i] = 0.5f;

    float sim = opad_compute_frame_similarity(frame, frame, SIM_LEN);
    EXPECT_NEAR(1.0f, sim, 0.01f);
}

TEST(Image, SimilarityNullPrevReturnsZero) {
    float curr[SIM_LEN];
    for (size_t i = 0; i < SIM_LEN; i++) curr[i] = 0.5f;

    float sim = opad_compute_frame_similarity(NULL, curr, SIM_LEN);
    EXPECT_FLOAT_EQ(0.0f, sim);
}

TEST(Image, SimilarityDifferentFramesLowerThanIdentical) {
    float prev[SIM_LEN], curr[SIM_LEN];
    for (size_t i = 0; i < SIM_LEN; i++) {
        prev[i] = 0.0f;
        curr[i] = 1.0f;
    }

    float sim = opad_compute_frame_similarity(prev, curr, SIM_LEN);
    EXPECT_LT(sim, 0.5f);
    EXPECT_GE(sim, 0.0f);
}

TEST(Image, SharpnessUniformGrayNearZero) {
    float gray[SHARP_LEN];
    for (size_t i = 0; i < SHARP_LEN; i++) gray[i] = 0.5f;

    float overall = -1.0f, qvar = -1.0f;
    opad_compute_face_sharpness(gray, SHARP_SIZE, &overall, &qvar);

    EXPECT_NEAR(0.0f, overall, 0.001f);
    EXPECT_NEAR(0.0f, qvar, 0.001f);
}

TEST(Image, SharpnessWithEdgesIsPositive) {
    float gray[SHARP_LEN];
    std::memset(gray, 0, sizeof(gray));

    // Create vertical stripes alternating 0 and 1
    for (size_t y = 0; y < SHARP_SIZE; y++) {
        for (size_t x = 0; x < SHARP_SIZE; x++) {
            gray[y * SHARP_SIZE + x] = (x % 2 == 0) ? 0.0f : 1.0f;
        }
    }

    float overall = 0.0f, qvar = 0.0f;
    opad_compute_face_sharpness(gray, SHARP_SIZE, &overall, &qvar);

    EXPECT_GT(overall, 0.0f);
}

TEST(Image, SharpnessQuadrantVarianceUniformImageNearZero) {
    float gray[SHARP_LEN];
    for (size_t i = 0; i < SHARP_LEN; i++) gray[i] = 0.3f;

    float overall = 0.0f, qvar = -1.0f;
    opad_compute_face_sharpness(gray, SHARP_SIZE, &overall, &qvar);

    EXPECT_NEAR(0.0f, qvar, 0.001f);
}
