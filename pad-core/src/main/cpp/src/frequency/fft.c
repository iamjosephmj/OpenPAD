/**
 * @file fft.c
 * @brief Radix-2 Cooley-Tukey FFT implementation.
 *
 * A lightweight, allocation-free, in-place FFT for power-of-2 lengths.
 * Used internally by the moiré detector for 64-point 2D transforms.
 *
 * Algorithm:
 *   1. Bit-reversal permutation of the input arrays.
 *   2. Log2(N) butterfly stages with twiddle factors computed on the fly.
 *
 * Performance: ~10 µs for a 64-point 1D transform on ARM Cortex-A76.
 */

#include "fft.h"
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void opad_fft_1d(float* re, float* im, size_t n) {
    /* Bit-reversal permutation */
    for (size_t i = 1, j = 0; i < n; i++) {
        size_t bit = n >> 1;
        while (j & bit) { j ^= bit; bit >>= 1; }
        j ^= bit;
        if (i < j) {
            float t;
            t = re[i]; re[i] = re[j]; re[j] = t;
            t = im[i]; im[i] = im[j]; im[j] = t;
        }
    }

    /* Butterfly stages */
    for (size_t len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * (float)M_PI / (float)len;
        float wre = cosf(ang), wim = sinf(ang);
        for (size_t i = 0; i < n; i += len) {
            float cur_re = 1.0f, cur_im = 0.0f;
            for (size_t j = 0; j < len / 2; j++) {
                size_t u = i + j, v = i + j + len / 2;
                float tre = re[v] * cur_re - im[v] * cur_im;
                float tim = re[v] * cur_im + im[v] * cur_re;
                re[v] = re[u] - tre;
                im[v] = im[u] - tim;
                re[u] += tre;
                im[u] += tim;
                float new_re = cur_re * wre - cur_im * wim;
                cur_im = cur_re * wim + cur_im * wre;
                cur_re = new_re;
            }
        }
    }
}
