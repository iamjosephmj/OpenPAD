/**
 * @file similarity.c
 * @brief Frame-to-frame similarity via mean absolute difference.
 *
 * Compares two 32x32 downsampled grayscale frames. A score near 1.0
 * means the frames are nearly identical (possible static/replayed content).
 * Normal camera feeds with a live person typically score 0.85–0.95.
 */

#include <openpad/image.h>

float opad_compute_frame_similarity(const float* prev, const float* curr, size_t len) {
    if (!prev || !curr || len == 0) return 0.0f;
    float sum_diff = 0.0f;
    for (size_t i = 0; i < len; i++) {
        float d = prev[i] - curr[i];
        sum_diff += (d < 0.0f) ? -d : d;
    }
    float result = 1.0f - sum_diff / (float)len;
    return result > 0.0f ? result : 0.0f;
}
