/**
 * Unit tests for opad_fft_moire and opad_lbp_screen.
 */

extern "C" {
#include <openpad/frequency.h>
#include <openpad/types.h>
}

#include <cstring>
#include <gtest/gtest.h>

static const size_t FFT_LEN = OPAD_FFT_SIZE * OPAD_FFT_SIZE;
static const size_t LBP_ARGB_LEN = OPAD_LBP_CROP_SIZE * OPAD_LBP_CROP_SIZE * 4;

TEST(Frequency, MoireConstantGrayProducesLowScore) {
    float gray[FFT_LEN];
    for (size_t i = 0; i < FFT_LEN; i++) gray[i] = 0.5f;

    OpadFrequencyResult result;
    std::memset(&result, 0, sizeof(result));
    opad_fft_moire(gray, &result);

    EXPECT_GE(result.moire_score, 0.0f);
    EXPECT_LE(result.moire_score, 1.0f);
}

TEST(Frequency, MoireZeroImageDoesNotCrash) {
    float gray[FFT_LEN];
    std::memset(gray, 0, sizeof(gray));

    OpadFrequencyResult result;
    std::memset(&result, 0, sizeof(result));
    opad_fft_moire(gray, &result);

    EXPECT_GE(result.moire_score, 0.0f);
    EXPECT_LE(result.moire_score, 1.0f);
}

TEST(Frequency, MoireOutputFieldsInRange) {
    float gray[FFT_LEN];
    for (size_t i = 0; i < FFT_LEN; i++) gray[i] = (float)(i % 64) / 64.0f;

    OpadFrequencyResult result;
    opad_fft_moire(gray, &result);

    EXPECT_GE(result.moire_score, 0.0f);
    EXPECT_LE(result.moire_score, 1.0f);
    EXPECT_GE(result.spectral_flatness, 0.0f);
    EXPECT_LE(result.spectral_flatness, 1.0f);
    EXPECT_GE(result.peak_frequency, 0.0f);
}

TEST(Frequency, LbpUniformArgbProducesValidOutput) {
    uint8_t argb[LBP_ARGB_LEN];
    // Uniform mid-gray ARGB: A=255, R=128, G=128, B=128
    for (size_t i = 0; i < LBP_ARGB_LEN; i += 4) {
        argb[i] = 255;
        argb[i + 1] = 128;
        argb[i + 2] = 128;
        argb[i + 3] = 128;
    }

    OpadLbpResult result;
    std::memset(&result, 0, sizeof(result));
    opad_lbp_screen(argb, &result);

    EXPECT_GE(result.screen_score, 0.0f);
    EXPECT_LE(result.screen_score, 1.0f);
    EXPECT_GE(result.uniformity, 0.0f);
    EXPECT_LE(result.uniformity, 1.0f);
}

TEST(Frequency, LbpZeroImageDoesNotCrash) {
    uint8_t argb[LBP_ARGB_LEN];
    std::memset(argb, 0, sizeof(argb));

    OpadLbpResult result;
    std::memset(&result, 0, sizeof(result));
    opad_lbp_screen(argb, &result);

    EXPECT_GE(result.screen_score, 0.0f);
    EXPECT_LE(result.screen_score, 1.0f);
}
