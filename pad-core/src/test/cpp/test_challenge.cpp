/**
 * Unit tests for opad_challenge_* functions.
 */

extern "C" {
#include <openpad/challenge.h>
#include <openpad/config.h>
#include <openpad/types.h>
}

#include <cstring>
#include <gtest/gtest.h>

class ChallengeTest : public ::testing::Test {
protected:
    OpadConfig cfg;
    OpadMovementChallenge* mc = nullptr;

    void SetUp() override {
        opad_config_default(&cfg);
        mc = opad_challenge_create(&cfg);
        ASSERT_NE(nullptr, mc);
    }

    void TearDown() override {
        opad_challenge_destroy(mc);
    }

    OpadChallengeFrameInput makeInput(
        bool hasFace = false,
        OpadPadStatus status = OPAD_STATUS_ANALYZING
    ) {
        OpadChallengeFrameInput input;
        std::memset(&input, 0, sizeof(input));
        input.face = nullptr;
        input.pad_status = status;
        return input;
    }
};

TEST_F(ChallengeTest, CreateAndDestroyLifecycle) {
    SUCCEED();
}

TEST_F(ChallengeTest, InitialPhaseIsIdle) {
    OpadChallengeOutput out;
    std::memset(&out, 0, sizeof(out));
    auto input = makeInput();
    opad_challenge_on_frame(mc, &input, &out);

    // After the first frame, phase transitions from IDLE to ANALYZING
    EXPECT_EQ(OPAD_PHASE_ANALYZING, out.phase);
}

TEST_F(ChallengeTest, AdvanceToLiveUpdatesPhase) {
    OpadChallengeOutput out;
    std::memset(&out, 0, sizeof(out));

    auto input = makeInput();
    opad_challenge_on_frame(mc, &input, &out);
    opad_challenge_advance_to_live(mc);

    opad_challenge_on_frame(mc, &input, &out);
    EXPECT_EQ(OPAD_PHASE_LIVE, out.phase);
}

TEST_F(ChallengeTest, AdvanceToDoneUpdatesPhase) {
    OpadChallengeOutput out;
    std::memset(&out, 0, sizeof(out));

    opad_challenge_advance_to_done(mc);

    auto input = makeInput();
    opad_challenge_on_frame(mc, &input, &out);
    EXPECT_EQ(OPAD_PHASE_DONE, out.phase);
}

TEST_F(ChallengeTest, HandleSpoofReturnsFalseUnderMaxAttempts) {
    bool maxReached = opad_challenge_handle_spoof(mc);
    EXPECT_FALSE(maxReached);
}

TEST_F(ChallengeTest, ResetReturnsToIdle) {
    OpadChallengeOutput out;
    std::memset(&out, 0, sizeof(out));

    auto input = makeInput();
    opad_challenge_on_frame(mc, &input, &out);
    EXPECT_NE(OPAD_PHASE_IDLE, out.phase);

    opad_challenge_reset(mc);
    opad_challenge_on_frame(mc, &input, &out);
    // After reset + first frame, transitions from IDLE to ANALYZING
    EXPECT_EQ(OPAD_PHASE_ANALYZING, out.phase);
}

TEST_F(ChallengeTest, HandleSpoofReachesMaxAttempts) {
    cfg.max_spoof_attempts = 2;
    opad_challenge_destroy(mc);
    mc = opad_challenge_create(&cfg);
    ASSERT_NE(nullptr, mc);

    bool reached1 = opad_challenge_handle_spoof(mc);
    EXPECT_FALSE(reached1);

    bool reached2 = opad_challenge_handle_spoof(mc);
    EXPECT_TRUE(reached2);
}
