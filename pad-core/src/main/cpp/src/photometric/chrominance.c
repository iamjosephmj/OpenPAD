/**
 * @file chrominance.c
 * @brief Skin chrominance variance analysis.
 *
 * Real skin under natural lighting exhibits varied chrominance (YCbCr)
 * due to sub-surface scattering, blood flow variation, and uneven
 * melanin distribution. Screen-reproduced skin has an artificially
 * tight chrominance distribution because displays quantize color.
 *
 * We compute the bivariate (Cb, Cr) covariance matrix over skin pixels
 * and derive a spread metric from its determinant. Low spread + low
 * standard deviation indicates screen reproduction.
 */

#include "photometric_internal.h"
#include <math.h>

float opad_analyze_chrominance(const uint8_t* argb) {
    float s_cb = 0, s_cr = 0, s_cb2 = 0, s_cr2 = 0, s_cbcr = 0;
    size_t skin_n = 0;

    for (size_t i = 0; i < PHOTO_CROP * PHOTO_CROP; i++) {
        float r, g, b;
        photo_pixel_rgb(argb, i, &r, &g, &b);
        float y  = 0.299f * r + 0.587f * g + 0.114f * b;
        float cb = 128.0f + (-0.169f * r - 0.331f * g + 0.500f * b);
        float cr = 128.0f + ( 0.500f * r - 0.419f * g - 0.081f * b);
        if (photo_is_skin(y, cb, cr)) {
            s_cb += cb; s_cr += cr;
            s_cb2 += cb * cb; s_cr2 += cr * cr;
            s_cbcr += cb * cr;
            skin_n++;
        }
    }
    if (skin_n < MIN_SKIN_PX) return 0.5f;

    float n    = (float)skin_n;
    float m_cb = s_cb / n,  m_cr = s_cr / n;
    float v_cb = s_cb2 / n - m_cb * m_cb;
    float v_cr = s_cr2 / n - m_cr * m_cr;
    float cov  = s_cbcr / n - m_cb * m_cr;
    float det  = v_cb * v_cr - cov * cov;
    if (det < 0.0f) det = 0.0f;

    float spread   = sqrtf(det);
    float std_cb   = sqrtf(v_cb > 0 ? v_cb : 0);
    float std_cr   = sqrtf(v_cr > 0 ? v_cr : 0);
    float avg_std  = (std_cb + std_cr) / 2.0f;

    float std_score    = photo_clamp01((avg_std - 3.0f) / 7.0f);
    float spread_score = photo_clamp01((spread  - 8.0f) / 40.0f);
    return std_score * 0.5f + spread_score * 0.5f;
}
