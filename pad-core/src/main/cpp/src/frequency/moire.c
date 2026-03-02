/**
 * @file moire.c
 * @brief FFT-based moiré pattern detection for screen spoof identification.
 *
 * Screens display images through a sub-pixel grid that creates periodic
 * interference patterns (moiré) when captured by a camera. This module
 * detects those patterns by:
 *
 *   1. Applying a 2D Hann window to suppress spectral leakage.
 *   2. Computing a 2D FFT via row-then-column 1D FFTs.
 *   3. Computing the radially averaged power spectrum.
 *   4. Measuring the mid-band energy ratio (screen moiré concentrates
 *      energy in the mid-frequency range).
 *   5. Measuring spectral flatness (geometric/arithmetic mean ratio).
 *
 * Outputs:
 *   - moire_score: high (>0.6) indicates likely screen capture.
 *   - peak_frequency: dominant radial frequency bin.
 *   - spectral_flatness: 1.0 = white noise, low = peaked spectrum.
 */

#include <openpad/frequency.h>
#include "fft.h"
#include <math.h>
#include <string.h>

#define FFT_N OPAD_FFT_SIZE

static void apply_hann_window(float* data) {
    for (size_t y = 0; y < FFT_N; y++) {
        float wy = 0.5f * (1.0f - cosf(2.0f * (float)M_PI * (float)y / (float)FFT_N));
        for (size_t x = 0; x < FFT_N; x++) {
            float wx = 0.5f * (1.0f - cosf(2.0f * (float)M_PI * (float)x / (float)FFT_N));
            data[y * FFT_N + x] *= wy * wx;
        }
    }
}

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void opad_fft_moire(const float* gray64, OpadFrequencyResult* out) {
    out->moire_score      = 0.0f;
    out->peak_frequency   = 0.0f;
    out->spectral_flatness = 1.0f;
    if (!gray64) return;

    float re[FFT_N * FFT_N];
    float im[FFT_N * FFT_N];
    memcpy(re, gray64, FFT_N * FFT_N * sizeof(float));
    memset(im, 0, sizeof(im));
    apply_hann_window(re);

    /* Row-wise 1D FFTs */
    for (size_t y = 0; y < FFT_N; y++)
        opad_fft_1d(re + y * FFT_N, im + y * FFT_N, FFT_N);

    /* Column-wise 1D FFTs */
    float col_re[FFT_N], col_im[FFT_N];
    for (size_t x = 0; x < FFT_N; x++) {
        for (size_t y = 0; y < FFT_N; y++) {
            col_re[y] = re[y * FFT_N + x];
            col_im[y] = im[y * FFT_N + x];
        }
        opad_fft_1d(col_re, col_im, FFT_N);
        for (size_t y = 0; y < FFT_N; y++) {
            re[y * FFT_N + x] = col_re[y];
            im[y * FFT_N + x] = col_im[y];
        }
    }

    /* Power spectrum and radial averaging */
    size_t half = FFT_N / 2;
    double power[FFT_N * FFT_N];
    for (size_t i = 0; i < FFT_N * FFT_N; i++)
        power[i] = (double)re[i] * re[i] + (double)im[i] * im[i];
    power[0] = 0.0;

    double radial_bins[FFT_N / 2];
    size_t radial_counts[FFT_N / 2];
    memset(radial_bins,   0, sizeof(radial_bins));
    memset(radial_counts, 0, sizeof(radial_counts));

    for (size_t y = 0; y < FFT_N; y++) {
        for (size_t x = 0; x < FFT_N; x++) {
            int fy = (y <= half) ? (int)y : (int)y - (int)FFT_N;
            int fx = (x <= half) ? (int)x : (int)x - (int)FFT_N;
            size_t radius = (size_t)sqrt((double)(fx * fx + fy * fy));
            if (radius >= 1 && radius < half) {
                radial_bins[radius] += power[y * FFT_N + x];
                radial_counts[radius]++;
            }
        }
    }
    for (size_t i = 0; i < half; i++) {
        if (radial_counts[i] > 0)
            radial_bins[i] /= (double)radial_counts[i];
    }

    /* Moiré score: mid-band energy ratio */
    double total_energy = 0.0;
    for (size_t i = 0; i < half; i++) total_energy += radial_bins[i];

    size_t mid_low  = half / 4;
    size_t mid_high = half * 3 / 4;
    double mid_energy = 0.0;
    for (size_t i = mid_low; i < mid_high; i++) mid_energy += radial_bins[i];

    float moire = (total_energy > 0.0) ? (float)(mid_energy / total_energy) : 0.0f;
    if (moire < 0.0f) moire = 0.0f;
    if (moire > 1.0f) moire = 1.0f;
    out->moire_score = moire;

    /* Peak frequency */
    size_t peak_idx = 1;
    double peak_val = 0.0;
    for (size_t i = 1; i < half; i++) {
        if (radial_bins[i] > peak_val) {
            peak_val = radial_bins[i];
            peak_idx = i;
        }
    }
    out->peak_frequency = (float)peak_idx;

    /* Spectral flatness */
    double log_sum = 0.0, arith_sum = 0.0;
    size_t non_zero = 0;
    for (size_t i = 0; i < half; i++) {
        if (radial_bins[i] > 0.0) {
            log_sum += log(radial_bins[i]);
            arith_sum += radial_bins[i];
            non_zero++;
        }
    }
    if (non_zero > 1 && arith_sum > 0.0) {
        double geo  = exp(log_sum / (double)non_zero);
        double ari  = arith_sum / (double)non_zero;
        float sf = (float)(geo / ari);
        if (sf < 0.0f) sf = 0.0f;
        if (sf > 1.0f) sf = 1.0f;
        out->spectral_flatness = sf;
    }
}
