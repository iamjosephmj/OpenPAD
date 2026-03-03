/**
 * Unit tests for opad_photometric_analyze.
 */

extern "C" {
#include <openpad/photometric.h>
#include <openpad/types.h>
}

#include <cstring>
#include <gtest/gtest.h>

static const size_t PHOTO_ARGB_LEN = OPAD_PHOTO_CROP_SIZE * OPAD_PHOTO_CROP_SIZE * 4;

TEST(Photometric, UniformGrayProducesValidScores) {
    uint8_t argb[PHOTO_ARGB_LEN];
    // Uniform skin-ish tone: A=255, R=180, G=140, B=120
    for (size_t i = 0; i < PHOTO_ARGB_LEN; i += 4) {
        argb[i] = 255;
        argb[i + 1] = 180;
        argb[i + 2] = 140;
        argb[i + 3] = 120;
    }

    OpadPhotometricResult result;
    std::memset(&result, 0, sizeof(result));
    opad_photometric_analyze(argb, &result);

    EXPECT_GE(result.combined_score, 0.0f);
    EXPECT_LE(result.combined_score, 1.0f);
    EXPECT_GE(result.specular_score, 0.0f);
    EXPECT_LE(result.specular_score, 1.0f);
    EXPECT_GE(result.chrominance_score, 0.0f);
    EXPECT_LE(result.chrominance_score, 1.0f);
    EXPECT_GE(result.edge_dof_score, 0.0f);
    EXPECT_LE(result.edge_dof_score, 1.0f);
    EXPECT_GE(result.lighting_score, 0.0f);
    EXPECT_LE(result.lighting_score, 1.0f);
}

TEST(Photometric, AllZerosDoesNotCrash) {
    uint8_t argb[PHOTO_ARGB_LEN];
    std::memset(argb, 0, sizeof(argb));

    OpadPhotometricResult result;
    std::memset(&result, 0, sizeof(result));
    opad_photometric_analyze(argb, &result);

    EXPECT_GE(result.combined_score, 0.0f);
    EXPECT_LE(result.combined_score, 1.0f);
}

TEST(Photometric, PureWhiteProducesValidScores) {
    uint8_t argb[PHOTO_ARGB_LEN];
    // Pure white: A=255, R=255, G=255, B=255
    for (size_t i = 0; i < PHOTO_ARGB_LEN; i += 4) {
        argb[i] = 255;
        argb[i + 1] = 255;
        argb[i + 2] = 255;
        argb[i + 3] = 255;
    }

    OpadPhotometricResult result;
    std::memset(&result, 0, sizeof(result));
    opad_photometric_analyze(argb, &result);

    EXPECT_GE(result.combined_score, 0.0f);
    EXPECT_LE(result.combined_score, 1.0f);
}
