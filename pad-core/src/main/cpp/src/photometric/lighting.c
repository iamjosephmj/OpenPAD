/**
 * @file lighting.c
 * @brief Lighting color-temperature gradient analysis.
 *
 * Real faces under point or mixed lighting show a color-temperature
 * gradient: highlights (close to the light source) are warmer (higher
 * R/B ratio) than shadows. This gradient is absent in screen-displayed
 * or printed faces where illumination is uniform.
 *
 * Algorithm:
 *   1. Extract skin pixels and compute luminance + R/B ratio.
 *   2. Sort skin pixels by luminance.
 *   3. Compare average R/B ratio of the darkest 30% vs. brightest 30%.
 *   4. A positive difference (bright = warmer) is the natural direction
 *      for incandescent/warm lighting and gets a bonus.
 */

#include "photometric_internal.h"
#include <math.h>

float opad_analyze_lighting(const uint8_t* argb) {
    typedef struct { float lum; float rb; } SkinPx;
    SkinPx skin[OPAD_PHOTO_CROP_SIZE * OPAD_PHOTO_CROP_SIZE];
    size_t skin_n = 0;

    for (size_t i = 0; i < PHOTO_CROP * PHOTO_CROP; i++) {
        float r, g, b;
        photo_pixel_rgb(argb, i, &r, &g, &b);
        float lum = 0.299f * r + 0.587f * g + 0.114f * b;
        float cb  = 128.0f + (-0.169f * r - 0.331f * g + 0.500f * b);
        float cr  = 128.0f + ( 0.500f * r - 0.419f * g - 0.081f * b);
        if (photo_is_skin(lum, cb, cr)) {
            skin[skin_n].lum = lum;
            skin[skin_n].rb  = b > 5.0f ? r / b : r / 5.0f;
            skin_n++;
        }
    }
    if (skin_n < MIN_SKIN_PX) return 0.5f;

    /* Insertion sort by luminance (max 6400 elements) */
    for (size_t i = 1; i < skin_n; i++) {
        SkinPx key = skin[i];
        size_t j = i;
        while (j > 0 && skin[j - 1].lum > key.lum) {
            skin[j] = skin[j - 1];
            j--;
        }
        skin[j] = key;
    }

    size_t dark_n = (size_t)((float)skin_n * 0.3f);
    if (dark_n < 10) dark_n = 10;
    if (dark_n > skin_n) dark_n = skin_n;
    size_t bright_n = (size_t)((float)skin_n * 0.3f);
    if (bright_n < 10) bright_n = 10;
    if (bright_n > skin_n) bright_n = skin_n;

    float dark_rb = 0.0f;
    for (size_t i = 0; i < dark_n; i++) dark_rb += skin[i].rb;
    dark_rb /= (float)dark_n;

    float bright_rb = 0.0f;
    for (size_t i = skin_n - bright_n; i < skin_n; i++) bright_rb += skin[i].rb;
    bright_rb /= (float)bright_n;

    float ct_diff = bright_rb - dark_rb;
    float ct_abs  = ct_diff < 0.0f ? -ct_diff : ct_diff;
    float ct_score = photo_clamp01((ct_abs - 0.05f) / 0.30f);
    float direction_bonus = ct_diff > 0.05f ? 0.15f : 0.0f;
    return photo_clamp01(ct_score * 0.85f + direction_bonus);
}
