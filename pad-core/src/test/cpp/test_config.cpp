/**
 * Unit tests for opad_config_default and opad_config_parse.
 */

#include <openpad/config.h>
#include <openpad/types.h>
#include <cstring>
#include <gtest/gtest.h>

extern "C" {
void opad_config_default(OpadConfig* out);
bool opad_config_parse(const uint8_t* bytes, size_t len, OpadConfig* out);
}

TEST(Config, DefaultValues) {
    OpadConfig cfg;
    opad_config_default(&cfg);

    EXPECT_FLOAT_EQ(0.55f, cfg.min_face_confidence);
    EXPECT_FLOAT_EQ(0.5f, cfg.texture_genuine_threshold);
    EXPECT_EQ(3, cfg.min_frames_for_decision);
    EXPECT_EQ(15, cfg.sliding_window_size);
    EXPECT_EQ(2, cfg.enter_consecutive);
    EXPECT_EQ(3, cfg.exit_consecutive);
    EXPECT_FLOAT_EQ(0.4f, cfg.depth_flatness_threshold);
    EXPECT_FLOAT_EQ(0.6f, cfg.moire_threshold);
    EXPECT_FLOAT_EQ(0.7f, cfg.lbp_screen_threshold);
    EXPECT_EQ(8, cfg.max_fps);
}

TEST(Config, ParseTooShortReturnsFalseAndAppliesDefaults) {
    uint8_t short_buf[100];
    std::memset(short_buf, 0, sizeof(short_buf));

    OpadConfig cfg;
    bool ok = opad_config_parse(short_buf, 100, &cfg);

    EXPECT_FALSE(ok);
    EXPECT_FLOAT_EQ(0.55f, cfg.min_face_confidence);
}

TEST(Config, ParseValidBuffer) {
    uint8_t buf[172];
    std::memset(buf, 0, sizeof(buf));

    // Write a few values at known offsets (little-endian)
    float f = 0.75f;
    std::memcpy(buf + 0, &f, 4);
    int32_t i = 10;
    std::memcpy(buf + 128, &i, 4);

    OpadConfig cfg;
    bool ok = opad_config_parse(buf, 172, &cfg);

    EXPECT_TRUE(ok);
    EXPECT_FLOAT_EQ(0.75f, cfg.min_face_confidence);
    EXPECT_EQ(10, cfg.min_frames_for_decision);
}
