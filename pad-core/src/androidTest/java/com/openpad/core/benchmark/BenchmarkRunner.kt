package com.openpad.core.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openpad.core.PadConfig
import com.openpad.core.PadPipeline
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentation test that runs the full PAD pipeline against a benchmark dataset.
 *
 * ## Usage
 *
 * 1. Prepare dataset frames + manifest.json (see DatasetLoader for format)
 * 2. Push to device:
 *    ```
 *    adb push ./benchmark_data/ /data/local/tmp/openpad-benchmark/
 *    ```
 * 3. Run:
 *    ```
 *    adb shell am instrument -w \
 *        -e class com.openpad.core.benchmark.BenchmarkRunner \
 *        com.openpad.core.test/androidx.test.runner.AndroidJUnitRunner
 *    ```
 * 4. Pull results: `adb pull /data/local/tmp/openpad-benchmark/results/`
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkRunner {

    @Test
    fun runBenchmark() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()

        // Use /data/local/tmp/ (no storage permissions needed) or app-specific external dir
        val datasetPath = args.getString("dataset_path")
            ?: "/data/local/tmp/openpad-benchmark"
        val baseDir = File(datasetPath)
        require(baseDir.exists()) { "Dataset not found at $baseDir. Push data first." }

        // Write results to app's external files dir (accessible via adb)
        val resultsDir = context.getExternalFilesDir("benchmark-results")
            ?: File(context.filesDir, "benchmark-results")
        resultsDir.mkdirs()

        Log.i(TAG, "=== Open-PAD Benchmark ===")
        Log.i(TAG, "Dataset: $baseDir")
        Log.i(TAG, "Results output: $resultsDir")

        // Initialize pipeline
        val config = PadConfig.Default
        val pipeline = PadPipeline.create(context, config)
        val processor = BenchmarkFrameProcessor(pipeline, config)
        val loader = DatasetLoader(baseDir)
        val writer = BenchmarkResultWriter(resultsDir)

        val videos = loader.loadManifest()
        Log.i(TAG, "Loaded ${videos.size} videos from manifest")

        var totalFrames = 0
        var totalTimeMs = 0L

        for ((index, video) in videos.withIndex()) {
            // Reset temporal state between videos + reclaim memory
            processor.resetState()
            System.gc()

            val frames = loader.getFrameFiles(video)
            if (frames.isEmpty()) {
                Log.w(TAG, "Skipping ${video.id}: no frames found in ${video.framesDir}")
                continue
            }

            val frameScores = mutableListOf<FrameScore>()
            val videoStartMs = System.currentTimeMillis()

            for (frameFile in frames) {
                val bitmap = loader.loadFrame(frameFile)
                if (bitmap == null) {
                    Log.w(TAG, "Failed to decode: ${frameFile.name}")
                    continue
                }
                val score = processor.processFrame(bitmap)
                frameScores.add(score)
                bitmap.recycle()
            }

            val videoTimeMs = System.currentTimeMillis() - videoStartMs
            totalFrames += frameScores.size
            totalTimeMs += videoTimeMs

            writer.writeVideoResult(
                videoId = video.id,
                groundTruth = video.label,
                attackType = video.attackType,
                frameScores = frameScores
            )

            val lastStatus = frameScores.lastOrNull()?.stableStatus?.name ?: "N/A"
            val avgScore = if (frameScores.isNotEmpty()) {
                "%.3f".format(frameScores.map { it.aggregatedScore }.average())
            } else "N/A"

            Log.i(TAG, "[%d/%d] %s: %s (score=%s, %d frames, %dms)".format(
                index + 1, videos.size, video.id, lastStatus, avgScore,
                frameScores.size, videoTimeMs
            ))
        }

        val avgMs = if (totalFrames > 0) totalTimeMs / totalFrames else 0
        Log.i(TAG, "=== Benchmark Complete ===")
        Log.i(TAG, "Videos: ${videos.size}, Frames: $totalFrames, Avg: ${avgMs}ms/frame")
        Log.i(TAG, "Results: ${resultsDir.absolutePath}")
        Log.i(TAG, "Pull with: adb pull ${resultsDir.absolutePath} ./benchmark_results/")

        pipeline.close()
    }

    companion object {
        private const val TAG = "PAD-Benchmark"
    }
}
