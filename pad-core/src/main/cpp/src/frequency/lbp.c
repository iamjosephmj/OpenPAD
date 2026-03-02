/**
 * @file lbp.c
 * @brief LBP-derived screen artifact detection.
 *
 * Detects three screen-specific artifacts on a 64x64 ARGB face crop:
 *
 * ### 1. Color Banding (weight 0.40)
 * Screens have limited bit depth, causing quantized gradients. We detect
 * this by measuring the ratio of perfectly-flat pixels and single-step
 * gradients in smooth regions.
 *
 * ### 2. Focus Uniformity (weight 0.30)
 * Real cameras have depth-of-field variation; screens produce uniformly
 * sharp images. We measure Laplacian variance across four quadrants
 * and flag low coefficient-of-variation as suspicious.
 *
 * ### 3. Color Distribution (weight 0.30)
 * Screen-reproduced skin has an artificially tight YCbCr chrominance
 * distribution compared to real skin under natural lighting. We measure
 * the standard deviation of Cb and Cr channels over skin pixels.
 */

#include <openpad/frequency.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#define LBP_N OPAD_LBP_CROP_SIZE
#define SMOOTH_GRADIENT_THRESHOLD 8
#define MIN_SMOOTH_PIXELS  100
#define MIN_SKIN_PIXELS    200
#define WEIGHT_BANDING     0.40f
#define WEIGHT_FOCUS       0.30f
#define WEIGHT_COLOR       0.30f

static float clamp01(float v) {
    if (v < 0.0f) return 0.0f;
    if (v > 1.0f) return 1.0f;
    return v;
}

/* ---- Sub-detector 1: Color Banding ---- */

static float detect_color_banding(const uint8_t* argb) {
    int gray[LBP_N * LBP_N];
    for (size_t i = 0; i < LBP_N * LBP_N; i++) {
        size_t idx = i * 4;
        int r = argb[idx], g = argb[idx + 1], b = argb[idx + 2];
        gray[i] = (r * 77 + g * 150 + b * 29) >> 8;
    }

    int smooth = 0, flat = 0, uniform_step = 0;
    for (size_t y = 1; y + 1 < LBP_N; y++) {
        for (size_t x = 1; x + 1 < LBP_N; x++) {
            size_t idx = y * LBP_N + x;
            int gx = abs(gray[idx + 1] - gray[idx - 1]);
            int gy = abs(gray[idx + LBP_N] - gray[idx - LBP_N]);
            if (gx <= SMOOTH_GRADIENT_THRESHOLD && gy <= SMOOTH_GRADIENT_THRESHOLD) {
                smooth++;
                int hg = abs(gray[idx + 1] - gray[idx]);
                int vg = abs(gray[idx + LBP_N] - gray[idx]);
                if (hg == 0 && vg == 0) flat++;
                if (hg == 1 && vg == 1) uniform_step++;
            }
        }
    }
    if (smooth < MIN_SMOOTH_PIXELS) return 0.0f;

    float flat_ratio = (float)flat / (float)smooth;
    float step_ratio = (float)uniform_step / (float)smooth;
    float flat_score = clamp01((flat_ratio - 0.20f) / 0.30f);
    float step_score = clamp01((step_ratio - 0.08f) / 0.12f);
    return flat_score * 0.5f + step_score * 0.5f;
}

/* ---- Sub-detector 2: Focus Uniformity ---- */

static float region_variance(const float* data, size_t stride,
                              size_t y0, size_t y1, size_t x0, size_t x1) {
    float sum = 0.0f, sum_sq = 0.0f;
    size_t count = 0;
    for (size_t y = y0; y < y1; y++) {
        for (size_t x = x0; x < x1; x++) {
            float v = data[y * stride + x];
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

static float detect_focus_uniformity(const uint8_t* argb) {
    float gray[LBP_N * LBP_N];
    for (size_t i = 0; i < LBP_N * LBP_N; i++) {
        size_t idx = i * 4;
        gray[i] = (float)argb[idx] * 0.299f
                + (float)argb[idx + 1] * 0.587f
                + (float)argb[idx + 2] * 0.114f;
    }

    float lap[LBP_N * LBP_N];
    memset(lap, 0, sizeof(lap));
    for (size_t y = 1; y + 1 < LBP_N; y++) {
        for (size_t x = 1; x + 1 < LBP_N; x++) {
            size_t idx = y * LBP_N + x;
            lap[idx] = -4.0f * gray[idx]
                + gray[idx - 1] + gray[idx + 1]
                + gray[idx - LBP_N] + gray[idx + LBP_N];
        }
    }

    size_t half = LBP_N / 2;
    float q1 = region_variance(lap, LBP_N, 2, half - 1, 2, half - 1);
    float q2 = region_variance(lap, LBP_N, 2, half - 1, half, LBP_N - 2);
    float q3 = region_variance(lap, LBP_N, half, LBP_N - 2, 2, half - 1);
    float q4 = region_variance(lap, LBP_N, half, LBP_N - 2, half, LBP_N - 2);

    float mean = (q1 + q2 + q3 + q4) / 4.0f;
    if (mean < 1.0f) return 0.5f;

    float var = 0.0f;
    float qs[4] = {q1, q2, q3, q4};
    for (int i = 0; i < 4; i++) {
        float d = qs[i] - mean;
        var += d * d;
    }
    var /= 4.0f;
    float cv = sqrtf(var) / mean;
    return clamp01(1.0f - (cv / 0.5f));
}

/* ---- Sub-detector 3: Color Distribution ---- */

static float std_dev(const float* values, size_t count) {
    if (count < 2) return 0.0f;
    float sum = 0.0f;
    for (size_t i = 0; i < count; i++) sum += values[i];
    float mean = sum / (float)count;
    float var = 0.0f;
    for (size_t i = 0; i < count; i++) {
        float d = values[i] - mean;
        var += d * d;
    }
    var /= (float)count;
    return sqrtf(var > 0.0f ? var : 0.0f);
}

static float analyze_color_distribution(const uint8_t* argb) {
    float cb_values[LBP_N * LBP_N];
    float cr_values[LBP_N * LBP_N];
    size_t skin = 0;

    for (size_t i = 0; i < LBP_N * LBP_N; i++) {
        size_t idx = i * 4;
        float r = (float)argb[idx];
        float g = (float)argb[idx + 1];
        float b = (float)argb[idx + 2];
        float y  = 0.299f * r + 0.587f * g + 0.114f * b;
        float cb = 128.0f + (-0.169f * r - 0.331f * g + 0.500f * b);
        float cr = 128.0f + ( 0.500f * r - 0.419f * g - 0.081f * b);
        if (y >= 40.0f && y <= 250.0f && cb >= 77.0f && cb <= 127.0f
            && cr >= 133.0f && cr <= 173.0f) {
            cb_values[skin] = cb;
            cr_values[skin] = cr;
            skin++;
        }
    }
    if (skin < MIN_SKIN_PIXELS) return 0.5f;

    float cb_std = std_dev(cb_values, skin);
    float cr_std = std_dev(cr_values, skin);
    float avg_std = (cb_std + cr_std) / 2.0f;
    return clamp01(1.0f - (avg_std - 2.0f) / 8.0f);
}

/* ---- Public API ---- */

void opad_lbp_screen(const uint8_t* argb64, OpadLbpResult* out) {
    out->screen_score        = 0.0f;
    out->uniformity          = 0.0f;
    out->entropy             = 0.0f;
    out->channel_correlation = 0.0f;
    if (!argb64) return;

    float banding = detect_color_banding(argb64);
    float focus   = detect_focus_uniformity(argb64);
    float color   = analyze_color_distribution(argb64);
    float score   = banding * WEIGHT_BANDING + focus * WEIGHT_FOCUS + color * WEIGHT_COLOR;

    out->screen_score        = clamp01(score);
    out->uniformity          = focus;
    out->entropy             = banding;
    out->channel_correlation = color;
}
