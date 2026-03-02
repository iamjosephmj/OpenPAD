/**
 * @file photometric.h
 * @brief Photometric analysis of face crops for spoof detection.
 *
 * Analyzes an 80x80 ARGB face crop through four independent sub-analyzers,
 * each producing a score in [0,1] where higher values indicate more
 * "genuine" (real-face-like) characteristics:
 *
 * | Sub-analyzer     | What it detects                                      |
 * |------------------|------------------------------------------------------|
 * | **Specular**     | Natural specular highlights (forehead/nose/cheeks).  |
 * | **Chrominance**  | Natural skin color variance in YCbCr space.          |
 * | **Edge DOF**     | Depth-of-field variation across 4x4 grid blocks.     |
 * | **Lighting**     | Color-temperature gradient between dark/bright skin.  |
 *
 * The combined score is a weighted average (equal weights by default).
 * Screens and prints tend to fail on lighting (no color-temperature shift)
 * and chrominance (artificially tight color gamut).
 */

#ifndef OPENPAD_PHOTOMETRIC_H
#define OPENPAD_PHOTOMETRIC_H

#include "types.h"

/**
 * Run full photometric analysis on an 80x80 ARGB face crop.
 *
 * @param argb80  80x80 pixel buffer, ARGB byte order (A,R,G,B per pixel).
 * @param out     Filled with per-sub-analyzer scores and combined_score.
 */
void opad_photometric_analyze(const uint8_t* argb80, OpadPhotometricResult* out);

#endif /* OPENPAD_PHOTOMETRIC_H */
