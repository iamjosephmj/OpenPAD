/**
 * @file fft.h
 * @brief Internal header for the radix-2 Cooley-Tukey FFT.
 *
 * This is a module-private header (not in include/openpad/) because
 * external callers use opad_fft_moire() from frequency.h instead.
 */

#ifndef OPENPAD_INTERNAL_FFT_H
#define OPENPAD_INTERNAL_FFT_H

#include <stddef.h>

/**
 * In-place radix-2 Cooley-Tukey FFT.
 *
 * @param re  Real components (length @p n), modified in place.
 * @param im  Imaginary components (length @p n), modified in place.
 * @param n   Transform length; MUST be a power of 2.
 */
void opad_fft_1d(float* re, float* im, size_t n);

#endif /* OPENPAD_INTERNAL_FFT_H */
