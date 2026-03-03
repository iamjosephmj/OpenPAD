package com.openpad.core.depth

import kotlin.math.sqrt

/**
 * 3D depth statistics extracted from the CDCN 32x32 depth map.
 *
 * Real faces produce varied depth (high std dev, high quadrant variance),
 * while flat spoofs (photos, screens) produce uniform values.
 *
 * @param mean Mean depth value across the 32x32 map.
 * @param standardDeviation Std dev — low for flat spoofs, high for real 3D faces.
 * @param quadrantVariance Variance across 4 quadrant means (16x16 each).
 * @param minDepth Minimum depth value in the map.
 * @param maxDepth Maximum depth value in the map.
 */
data class DepthCharacteristics(
    val mean: Float,
    val standardDeviation: Float,
    val quadrantVariance: Float,
    val minDepth: Float,
    val maxDepth: Float
) {
    companion object {
        /**
         * Average multiple per-frame [DepthCharacteristics] into a single summary.
         * Returns null if the input list is empty.
         */
        fun average(frames: List<DepthCharacteristics>): DepthCharacteristics? {
            if (frames.isEmpty()) return null
            val n = frames.size
            return DepthCharacteristics(
                mean = frames.sumOf { it.mean.toDouble() }.toFloat() / n,
                standardDeviation = frames.sumOf { it.standardDeviation.toDouble() }.toFloat() / n,
                quadrantVariance = frames.sumOf { it.quadrantVariance.toDouble() }.toFloat() / n,
                minDepth = frames.sumOf { it.minDepth.toDouble() }.toFloat() / n,
                maxDepth = frames.sumOf { it.maxDepth.toDouble() }.toFloat() / n
            )
        }

        /**
         * Compute depth characteristics from a raw 32x32 depth map.
         */
        internal fun fromDepthMap(depthMap: Array<FloatArray>): DepthCharacteristics {
            val size = depthMap.size
            val half = size / 2

            var sum = 0.0
            var min = Float.MAX_VALUE
            var max = Float.MIN_VALUE
            val total = size * size

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val v = depthMap[y][x]
                    sum += v
                    if (v < min) min = v
                    if (v > max) max = v
                }
            }
            val mean = (sum / total).toFloat()

            // Standard deviation
            var varianceSum = 0.0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val diff = depthMap[y][x] - mean
                    varianceSum += diff * diff
                }
            }
            val stdDev = sqrt(varianceSum / total).toFloat()

            // Quadrant means (4 quadrants, each half x half)
            val quadrantSums = DoubleArray(4)
            val quadrantCount = half * half
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val qi = (if (y < half) 0 else 2) + (if (x < half) 0 else 1)
                    quadrantSums[qi] += depthMap[y][x]
                }
            }
            val quadrantMeans = FloatArray(4) { (quadrantSums[it] / quadrantCount).toFloat() }

            // Variance across quadrant means
            val qMean = quadrantMeans.average().toFloat()
            var qVar = 0f
            for (qm in quadrantMeans) {
                val d = qm - qMean
                qVar += d * d
            }
            qVar /= 4f

            return DepthCharacteristics(
                mean = mean,
                standardDeviation = stdDev,
                quadrantVariance = qVar,
                minDepth = min,
                maxDepth = max
            )
        }
    }
}
