/**
 * @file frequency.h
 * @brief Frequency-domain analysis: FFT moiré and LBP screen detection.
 *
 * Two independent detectors for screen/print spoof artifacts:
 *
 * ### FFT Moiré Detector
 * Computes a 2D 64-point radix-2 Cooley-Tukey FFT on a grayscale face crop
 * to detect periodic moiré patterns caused by screen sub-pixel grids.
 * A Hann window is applied to suppress spectral leakage. The moiré score
 * is the ratio of mid-band radial energy to total energy.
 *
 * ### LBP Screen Detector
 * Analyzes an ARGB face crop for three screen-specific artifacts:
 *   1. **Color banding** — quantized gradients from limited display bit depth.
 *   2. **Focus uniformity** — unnaturally flat focus across quadrants.
 *   3. **Color distribution** — abnormally tight skin chrominance (YCbCr).
 *
 * @see opad_fft_moire(), opad_lbp_screen()
 */

#ifndef OPENPAD_FREQUENCY_H
#define OPENPAD_FREQUENCY_H

#include "types.h"

/**
 * Detect moiré artifacts via 2D FFT on a grayscale face crop.
 *
 * @param gray64  64x64 grayscale float array, values in [0,1].
 * @param out     Filled with moire_score, peak_frequency, spectral_flatness.
 */
void opad_fft_moire(const float* gray64, OpadFrequencyResult* out);

/**
 * Detect screen artifacts via color banding, focus uniformity, and
 * chrominance analysis on an ARGB face crop.
 *
 * @param argb64  64x64 ARGB pixel buffer (A,R,G,B byte order per pixel).
 * @param out     Filled with screen_score, uniformity, entropy, channel_correlation.
 */
void opad_lbp_screen(const uint8_t* argb64, OpadLbpResult* out);

#endif /* OPENPAD_FREQUENCY_H */
