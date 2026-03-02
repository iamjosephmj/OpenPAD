/**
 * @file photometric.c
 * @brief Photometric analysis entry point — combines four sub-analyzers.
 *
 * Weights (default, equal):
 *   Specular 0.25 | Chrominance 0.25 | Edge DOF 0.25 | Lighting 0.25
 */

#include <openpad/photometric.h>
#include "photometric_internal.h"

#define W_SPEC  0.25f
#define W_CHROM 0.25f
#define W_EDGE  0.25f
#define W_LIGHT 0.25f

void opad_photometric_analyze(const uint8_t* argb80, OpadPhotometricResult* out) {
    out->specular_score    = 0.5f;
    out->chrominance_score = 0.5f;
    out->edge_dof_score    = 0.5f;
    out->lighting_score    = 0.5f;
    out->combined_score    = 0.5f;
    if (!argb80) return;

    out->specular_score    = opad_analyze_specular(argb80);
    out->chrominance_score = opad_analyze_chrominance(argb80);
    out->edge_dof_score    = opad_analyze_edge_dof(argb80);
    out->lighting_score    = opad_analyze_lighting(argb80);
    out->combined_score    = photo_clamp01(
        out->specular_score    * W_SPEC  +
        out->chrominance_score * W_CHROM +
        out->edge_dof_score    * W_EDGE  +
        out->lighting_score    * W_LIGHT);
}
