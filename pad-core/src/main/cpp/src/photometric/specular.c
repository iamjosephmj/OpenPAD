/**
 * @file specular.c
 * @brief Specular highlight analysis for liveness detection.
 *
 * Real faces under point or directional lighting produce concentrated
 * specular highlights on convex surfaces (forehead, nose tip, cheeks).
 * These highlights are:
 *   - Spatially concentrated (low variance of highlight pixel positions).
 *   - Biased toward the upper face (forehead catches overhead lights).
 *   - A small fraction of total pixels (1-12%).
 *
 * Screens produce diffuse, spread-out brightness with no natural highlight
 * concentration. Prints show no specular response at all.
 */

#include "photometric_internal.h"
#include <math.h>

#define SPECULAR_OFFSET     60.0f
#define MIN_HIGHLIGHT_PX    5
#define MAX_HIGHLIGHT_RATIO 0.12f

float opad_analyze_specular(const uint8_t* argb) {
    size_t total = PHOTO_CROP * PHOTO_CROP;
    float lum[OPAD_PHOTO_CROP_SIZE * OPAD_PHOTO_CROP_SIZE];
    float sum_lum = 0.0f;
    for (size_t i = 0; i < total; i++) {
        float r, g, b;
        photo_pixel_rgb(argb, i, &r, &g, &b);
        lum[i] = r * 0.299f + g * 0.587f + b * 0.114f;
        sum_lum += lum[i];
    }
    float mean_lum = sum_lum / (float)total;
    float threshold = mean_lum + SPECULAR_OFFSET;
    if (threshold > 245.0f) threshold = 245.0f;

    size_t hl_count = 0, upper_hl = 0;
    float sx = 0.0f, sy = 0.0f, sxx = 0.0f, syy = 0.0f;
    for (size_t y = 0; y < PHOTO_CROP; y++) {
        for (size_t x = 0; x < PHOTO_CROP; x++) {
            if (lum[y * PHOTO_CROP + x] >= threshold) {
                hl_count++;
                sx += (float)x; sy += (float)y;
                sxx += (float)x * (float)x;
                syy += (float)y * (float)y;
                if (y < PHOTO_CROP / 2) upper_hl++;
            }
        }
    }

    float hl_ratio = (float)hl_count / (float)total;
    if (hl_count < MIN_HIGHLIGHT_PX) return 0.3f;
    if (hl_ratio > MAX_HIGHLIGHT_RATIO) {
        float excess = (hl_ratio - MAX_HIGHLIGHT_RATIO) / 0.15f;
        return photo_clamp01(0.4f - excess * 0.3f);
    }

    float mx = sx / (float)hl_count;
    float my = sy / (float)hl_count;
    float vx = sxx / (float)hl_count - mx * mx;
    float vy = syy / (float)hl_count - my * my;
    float spatial       = sqrtf(vx + vy) / (float)PHOTO_CROP;
    float concentration = photo_clamp01(1.0f - spatial / 0.35f);
    float upper_ratio   = (float)upper_hl / (float)hl_count;
    float upper_score   = upper_ratio > 0.4f ? 1.0f : upper_ratio / 0.4f;
    return photo_clamp01(concentration * 0.6f + upper_score * 0.2f + 0.2f);
}
