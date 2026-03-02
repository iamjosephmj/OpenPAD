/**
 * @file ringbuf.h
 * @brief Fixed-size ring buffer for temporal sliding windows.
 *
 * A stack-allocated circular buffer with capacity up to OPAD_MAX_WINDOW_SIZE.
 * Supports push (overwrites oldest on full), indexed access, clear,
 * and statistical operations (variance).
 *
 * Capacity of 0 is handled gracefully: push is a no-op, count stays 0.
 */

#ifndef OPENPAD_RINGBUF_H
#define OPENPAD_RINGBUF_H

#include <openpad/types.h>

typedef struct {
    float  data[OPAD_MAX_WINDOW_SIZE];
    size_t head;
    size_t count;
    size_t capacity;
} OpadRingBuf;

/** Initialize with the given capacity (clamped to OPAD_MAX_WINDOW_SIZE). */
void opad_ring_init(OpadRingBuf* rb, size_t cap);

/** Push a value; overwrites oldest if full. No-op if capacity is 0. */
void opad_ring_push(OpadRingBuf* rb, float v);

/** Get the i-th element (0 = oldest). Returns 0 if capacity is 0. */
float opad_ring_get(const OpadRingBuf* rb, size_t i);

/** Reset count and head to 0 (capacity is preserved). */
void opad_ring_clear(OpadRingBuf* rb);

/** Compute population variance of buffered values. Returns 0 if count < 2. */
float opad_ring_variance(const OpadRingBuf* rb);

#endif /* OPENPAD_RINGBUF_H */
