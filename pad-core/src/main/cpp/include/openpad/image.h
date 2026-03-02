/**
 * @file image.h
 * @brief Frame-level image analysis: similarity and sharpness.
 *
 * These functions operate on small downsampled grayscale buffers and are
 * called once per frame before any face-specific analysis.
 *
 * - **Similarity**: Mean absolute difference between consecutive 32x32
 *   grayscale frames. Detects static/replayed content.
 * - **Sharpness**: Laplacian variance over a 64x64 face crop. Measures
 *   focus quality and detects printed/screen spoofs with flat focus.
 */

#ifndef OPENPAD_IMAGE_H
#define OPENPAD_IMAGE_H

#include "types.h"

/**
 * Compute frame-to-frame similarity (1.0 = identical, 0.0 = entirely different).
 *
 * @param prev  Previous frame's downsampled grayscale buffer, or NULL.
 * @param curr  Current frame's downsampled grayscale buffer.
 * @param len   Number of pixels (OPAD_SIMILARITY_SIZE^2).
 * @return Similarity score in [0, 1].
 */
float opad_compute_frame_similarity(const float* prev, const float* curr, size_t len);

/**
 * Compute face sharpness using Laplacian variance.
 *
 * Divides the crop into four quadrants and measures variance independently.
 * A genuine face typically shows higher sharpness with natural quadrant
 * variation (nose vs. cheeks), while flat spoofs are uniformly sharp/blurry.
 *
 * @param gray             64x64 grayscale face crop, values in [0,1].
 * @param size             Side length of the crop (64).
 * @param out_overall      Overall Laplacian variance (higher = sharper).
 * @param out_quadrant_var Variance across four quadrant sharpness values.
 */
void opad_compute_face_sharpness(const float* gray, size_t size,
                                  float* out_overall, float* out_quadrant_var);

#endif /* OPENPAD_IMAGE_H */
