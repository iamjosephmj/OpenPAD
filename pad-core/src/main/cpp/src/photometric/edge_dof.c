/**
 * @file edge_dof.c
 * @brief Edge depth-of-field analysis.
 *
 * Real cameras produce natural depth-of-field: the nose (close to lens)
 * is sharper than the ears/hair (farther away). We measure this by:
 *
 *   1. Converting the 80x80 ARGB crop to grayscale.
 *   2. Computing the Laplacian (edge response) at each pixel.
 *   3. Dividing into a 4x4 grid and computing Laplacian variance per block.
 *   4. Measuring the coefficient of variation (CV) across blocks.
 *
 * High CV = natural DOF variation = likely genuine.
 * Low CV = uniformly sharp/blurry = likely screen/print.
 */

#include "photometric_internal.h"
#include <math.h>

#define GRID_SIZE 4

float opad_analyze_edge_dof(const uint8_t* argb) {
    float gray[OPAD_PHOTO_CROP_SIZE * OPAD_PHOTO_CROP_SIZE];
    for (size_t i = 0; i < PHOTO_CROP * PHOTO_CROP; i++) {
        float r, g, b;
        photo_pixel_rgb(argb, i, &r, &g, &b);
        gray[i] = r * 0.299f + g * 0.587f + b * 0.114f;
    }

    size_t block_size = PHOTO_CROP / GRID_SIZE;
    float block_sharpness[GRID_SIZE * GRID_SIZE];

    for (size_t by = 0; by < GRID_SIZE; by++) {
        for (size_t bx = 0; bx < GRID_SIZE; bx++) {
            size_t y0 = by * block_size + 1;
            size_t y1 = (by + 1) * block_size;
            if (y1 > PHOTO_CROP - 1) y1 = PHOTO_CROP - 1;
            size_t x0 = bx * block_size + 1;
            size_t x1 = (bx + 1) * block_size;
            if (x1 > PHOTO_CROP - 1) x1 = PHOTO_CROP - 1;

            float sum = 0.0f, sum_sq = 0.0f;
            size_t count = 0;
            for (size_t y = y0; y < y1; y++) {
                for (size_t x = x0; x < x1; x++) {
                    size_t idx = y * PHOTO_CROP + x;
                    float lap = -4.0f * gray[idx]
                        + gray[idx - 1] + gray[idx + 1]
                        + gray[idx - PHOTO_CROP] + gray[idx + PHOTO_CROP];
                    sum += lap;
                    sum_sq += lap * lap;
                    count++;
                }
            }
            if (count > 0) {
                float m = sum / (float)count;
                float v = sum_sq / (float)count - m * m;
                block_sharpness[by * GRID_SIZE + bx] = v > 0.0f ? v : 0.0f;
            } else {
                block_sharpness[by * GRID_SIZE + bx] = 0.0f;
            }
        }
    }

    size_t n = GRID_SIZE * GRID_SIZE;
    float mean = 0.0f;
    for (size_t i = 0; i < n; i++) mean += block_sharpness[i];
    mean /= (float)n;
    if (mean < 10.0f) return 0.5f;

    float var = 0.0f;
    for (size_t i = 0; i < n; i++) {
        float d = block_sharpness[i] - mean;
        var += d * d;
    }
    var /= (float)n;
    float cv = sqrtf(var) / mean;
    return photo_clamp01((cv - 0.35f) / 0.8f);
}
