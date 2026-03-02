/**
 * @file sharpness.c
 * @brief Face sharpness analysis via Laplacian variance.
 *
 * Computes the Laplacian (discrete second derivative) over a 64x64
 * grayscale face crop, then measures variance. Higher variance indicates
 * sharper edges — real faces have high overall sharpness with natural
 * variation between quadrants (nose ridge vs. flat cheek areas).
 *
 * Print attacks often show uniformly sharp or uniformly blurry crops.
 * Screen attacks may show moiré-induced sharpness artifacts.
 */

#include <openpad/image.h>

static float laplacian_variance(const float* lap, size_t stride,
                                size_t y0, size_t y1, size_t x0, size_t x1) {
    float sum = 0.0f, sum_sq = 0.0f;
    size_t count = 0;
    for (size_t y = y0; y < y1; y++) {
        for (size_t x = x0; x < x1; x++) {
            float v = lap[y * stride + x];
            sum += v;
            sum_sq += v * v;
            count++;
        }
    }
    if (count == 0) return 0.0f;
    float mean = sum / (float)count;
    float var = sum_sq / (float)count - mean * mean;
    return var > 0.0f ? var : 0.0f;
}

void opad_compute_face_sharpness(const float* gray, size_t size,
                                  float* out_overall, float* out_quadrant_var) {
    *out_overall = 0.0f;
    *out_quadrant_var = 0.0f;
    if (size < 4 || !gray) return;

    size_t n = size * size;
    float lap[OPAD_SHARPNESS_SIZE * OPAD_SHARPNESS_SIZE];
    if (n > sizeof(lap) / sizeof(lap[0])) return;

    for (size_t i = 0; i < n; i++) lap[i] = 0.0f;

    for (size_t y = 1; y + 1 < size; y++) {
        for (size_t x = 1; x + 1 < size; x++) {
            size_t idx = y * size + x;
            lap[idx] = -4.0f * gray[idx]
                + gray[idx - 1] + gray[idx + 1]
                + gray[idx - size] + gray[idx + size];
        }
    }

    *out_overall = laplacian_variance(lap, size, 1, size - 1, 1, size - 1);

    size_t half = size / 2;
    float q[4];
    q[0] = laplacian_variance(lap, size, 1, half, 1, half);
    q[1] = laplacian_variance(lap, size, 1, half, half, size - 1);
    q[2] = laplacian_variance(lap, size, half, size - 1, 1, half);
    q[3] = laplacian_variance(lap, size, half, size - 1, half, size - 1);

    float q_mean = (q[0] + q[1] + q[2] + q[3]) / 4.0f;
    float qvar = 0.0f;
    for (int i = 0; i < 4; i++) {
        float d = q[i] - q_mean;
        qvar += d * d;
    }
    *out_quadrant_var = qvar / 4.0f;
    if (*out_quadrant_var < 0.0f) *out_quadrant_var = 0.0f;
}
