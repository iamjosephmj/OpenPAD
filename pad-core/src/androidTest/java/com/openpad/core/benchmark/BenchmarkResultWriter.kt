package com.openpad.core.benchmark

import com.openpad.core.aggregation.PadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Writes per-video benchmark results as JSON files to device storage.
 */
class BenchmarkResultWriter(private val outputDir: File) {

    init {
        outputDir.mkdirs()
    }

    fun writeVideoResult(
        videoId: String,
        groundTruth: String,
        attackType: String?,
        frameScores: List<FrameScore>
    ) {
        val decisiveFrames = frameScores.filter {
            it.stableStatus != PadStatus.ANALYZING && it.stableStatus != PadStatus.NO_FACE
        }

        val finalDecision = frameScores.lastOrNull()?.stableStatus?.name ?: "NO_FACE"
        val majorityDecision = computeMajorityDecision(decisiveFrames)

        val json = JSONObject().apply {
            put("video_id", videoId)
            put("ground_truth", groundTruth)
            put("attack_type", attackType ?: JSONObject.NULL)
            put("frame_count", frameScores.size)
            put("decisive_frame_count", decisiveFrames.size)
            put("final_decision", finalDecision)
            put("majority_decision", majorityDecision)

            // Mean scores across all frames with face detected
            val faceFrames = frameScores.filter { it.faceDetected }
            put("mean_aggregated_score", faceFrames.meanOf { it.aggregatedScore })
            put("mean_texture_genuine", faceFrames.meanOfNullable { it.textureGenuineScore })
            put("mean_depth_score", faceFrames.meanOfNullable { it.depthScore })
            put("mean_moire_score", faceFrames.meanOfNullable { it.moireScore })
            put("mean_lbp_screen_score", faceFrames.meanOfNullable { it.lbpScreenScore })
            put("mean_device_spoof_score", faceFrames.meanOfNullable { it.deviceSpoofScore })
            put("mean_photometric_combined", faceFrames.meanOfNullable { it.photometricCombinedScore })
            put("mean_head_movement_variance", faceFrames.meanOfNullable { it.headMovementVariance })
            put("blink_ever_detected", faceFrames.any { it.blinkDetected == true })
            put("mean_frame_similarity", faceFrames.meanOf { it.frameSimilarity })
            put("mean_face_sharpness", faceFrames.meanOf { it.faceSharpness })
            put("mean_processing_time_ms", faceFrames.meanOf { it.processingTimeMs.toFloat() })

            // Per-frame detail
            put("frames", JSONArray().apply {
                frameScores.forEachIndexed { index, score ->
                    put(JSONObject().apply {
                        put("index", index)
                        put("aggregated_score", score.aggregatedScore.toDouble())
                        put("raw_status", score.rawStatus.name)
                        put("stable_status", score.stableStatus.name)
                        put("face_detected", score.faceDetected)
                    })
                }
            })
        }

        val file = File(outputDir, "$videoId.json")
        file.writeText(json.toString(2))
    }

    private fun computeMajorityDecision(decisiveFrames: List<FrameScore>): String {
        if (decisiveFrames.isEmpty()) return "NO_FACE"
        val liveCount = decisiveFrames.count { it.stableStatus == PadStatus.LIVE }
        val spoofCount = decisiveFrames.count { it.stableStatus == PadStatus.SPOOF_SUSPECTED }
        return if (liveCount >= spoofCount) "LIVE" else "SPOOF_SUSPECTED"
    }

    private fun List<FrameScore>.meanOf(selector: (FrameScore) -> Float): Double {
        if (isEmpty()) return 0.0
        return map { selector(it).toDouble() }.average()
    }

    private fun List<FrameScore>.meanOfNullable(selector: (FrameScore) -> Float?): Any {
        val values = mapNotNull { selector(it) }
        return if (values.isEmpty()) JSONObject.NULL else values.map { it.toDouble() }.average()
    }
}
