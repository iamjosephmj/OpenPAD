/**
 * @file photometric_internal.h
 * @brief Shared utilities for the photometric sub-analyzers.
 */

#ifndef OPENPAD_PHOTOMETRIC_INTERNAL_H
#define OPENPAD_PHOTOMETRIC_INTERNAL_H

#include <openpad/types.h>

#define PHOTO_CROP OPAD_PHOTO_CROP_SIZE
#define MIN_SKIN_PX 100

static inline float photo_clamp01(float v) {
    if (v < 0.0f) return 0.0f;
    if (v > 1.0f) return 1.0f;
    return v;
}

static inline void photo_pixel_rgb(const uint8_t* argb, size_t i,
                                    float* r, float* g, float* b) {
    size_t idx = i * 4;
    *r = (float)argb[idx + 1];
    *g = (float)argb[idx + 2];
    *b = (float)argb[idx + 3];
}

static inline bool photo_is_skin(float y, float cb, float cr) {
    if (y < 16.0f || y > 235.0f) return false;
    bool primary  = cb >= 77.0f && cb <= 127.0f && cr >= 133.0f && cr <= 173.0f;
    bool dark_ext = y < 120.0f && cb >= 80.0f && cb <= 145.0f
                    && cr >= 118.0f && cr <= 155.0f;
    return primary || dark_ext;
}

/* Sub-analyzer function prototypes */
float opad_analyze_specular(const uint8_t* argb);
float opad_analyze_chrominance(const uint8_t* argb);
float opad_analyze_edge_dof(const uint8_t* argb);
float opad_analyze_lighting(const uint8_t* argb);

#endif /* OPENPAD_PHOTOMETRIC_INTERNAL_H */
