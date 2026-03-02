/**
 * @file ringbuf.c
 * @brief Fixed-size ring buffer implementation.
 */

#include "ringbuf.h"
#include <string.h>

void opad_ring_init(OpadRingBuf* rb, size_t cap) {
    rb->head     = 0;
    rb->count    = 0;
    rb->capacity = cap < OPAD_MAX_WINDOW_SIZE ? cap : OPAD_MAX_WINDOW_SIZE;
    memset(rb->data, 0, sizeof(rb->data));
}

void opad_ring_push(OpadRingBuf* rb, float v) {
    if (rb->capacity == 0) return;
    size_t idx = (rb->head + rb->count) % rb->capacity;
    if (rb->count < rb->capacity) {
        rb->data[idx] = v;
        rb->count++;
    } else {
        rb->data[rb->head] = v;
        rb->head = (rb->head + 1) % rb->capacity;
    }
}

float opad_ring_get(const OpadRingBuf* rb, size_t i) {
    if (rb->capacity == 0) return 0.0f;
    return rb->data[(rb->head + i) % rb->capacity];
}

void opad_ring_clear(OpadRingBuf* rb) {
    rb->head  = 0;
    rb->count = 0;
}

float opad_ring_variance(const OpadRingBuf* rb) {
    if (rb->count < 2) return 0.0f;
    float sum = 0.0f;
    for (size_t i = 0; i < rb->count; i++)
        sum += opad_ring_get(rb, i);
    float mean = sum / (float)rb->count;
    float var = 0.0f;
    for (size_t i = 0; i < rb->count; i++) {
        float d = opad_ring_get(rb, i) - mean;
        var += d * d;
    }
    return var / (float)rb->count;
}
