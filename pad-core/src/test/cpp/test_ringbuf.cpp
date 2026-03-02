/**
 * Unit tests for opad_ring_* functions.
 */

extern "C" {
#include <openpad/types.h>
#include "ringbuf.h"
}

#include <cmath>
#include <gtest/gtest.h>

TEST(RingBuf, Init) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 8);

    EXPECT_EQ(0u, rb.head);
    EXPECT_EQ(0u, rb.count);
    EXPECT_EQ(8u, rb.capacity);
}

TEST(RingBuf, InitClampsToMaxWindowSize) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 1000);

    EXPECT_EQ(64u, rb.capacity);
}

TEST(RingBuf, PushAndGet) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 4);

    opad_ring_push(&rb, 1.0f);
    opad_ring_push(&rb, 2.0f);
    opad_ring_push(&rb, 3.0f);

    EXPECT_EQ(3u, rb.count);
    EXPECT_FLOAT_EQ(1.0f, opad_ring_get(&rb, 0));
    EXPECT_FLOAT_EQ(2.0f, opad_ring_get(&rb, 1));
    EXPECT_FLOAT_EQ(3.0f, opad_ring_get(&rb, 2));
}

TEST(RingBuf, OverflowOverwritesOldest) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 3);

    opad_ring_push(&rb, 1.0f);
    opad_ring_push(&rb, 2.0f);
    opad_ring_push(&rb, 3.0f);
    opad_ring_push(&rb, 4.0f);

    EXPECT_EQ(3u, rb.count);
    EXPECT_FLOAT_EQ(2.0f, opad_ring_get(&rb, 0));
    EXPECT_FLOAT_EQ(3.0f, opad_ring_get(&rb, 1));
    EXPECT_FLOAT_EQ(4.0f, opad_ring_get(&rb, 2));
}

TEST(RingBuf, Clear) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 4);
    opad_ring_push(&rb, 1.0f);
    opad_ring_push(&rb, 2.0f);

    opad_ring_clear(&rb);

    EXPECT_EQ(0u, rb.count);
}

TEST(RingBuf, VarianceZeroForLessThanTwo) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 4);
    opad_ring_push(&rb, 1.0f);

    EXPECT_FLOAT_EQ(0.0f, opad_ring_variance(&rb));
}

TEST(RingBuf, Variance) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 4);
    opad_ring_push(&rb, 2.0f);
    opad_ring_push(&rb, 4.0f);
    opad_ring_push(&rb, 4.0f);

    float var = opad_ring_variance(&rb);
    float expected = 4.0f / 3.0f;
    EXPECT_NEAR(expected, var, 1e-5f);
}

TEST(RingBuf, ZeroCapacityPushIsNoOp) {
    OpadRingBuf rb;
    opad_ring_init(&rb, 0);

    opad_ring_push(&rb, 1.0f);

    EXPECT_EQ(0u, rb.count);
    EXPECT_FLOAT_EQ(0.0f, opad_ring_get(&rb, 0));
}
