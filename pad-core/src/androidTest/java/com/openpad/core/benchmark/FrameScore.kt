package com.openpad.core.benchmark

import com.openpad.core.aggregation.PadStatus
import org.json.JSONObject

/**
 * Per-frame output from the benchmark pipeline.
 * Captures every sub-score for offline threshold tuning and per-layer analysis.
 */
data class FrameScore(
    val rawStatus: PadStatus,
    val stableStatus: PadStatus,
    val aggregatedScore: Float,
    // Texture
    val textureGenuineScore: Float?,
    val textureSpoofScore: Float?,
    // Depth (cascaded: MN3 + CDCN)
    val depthScore: Float?,
    val mn3RealScore: Float?,
    val mn3SpoofScore: Float?,
    val cdcnDepthScore: Float?,
    val cdcnTriggered: Boolean?,
    val cdcnAvailable: Boolean?,
    // Frequency
    val moireScore: Float?,
    val lbpScreenScore: Float?,
    val spectralFlatness: Float?,
    // Device
    val deviceDetected: Boolean?,
    val deviceSpoofScore: Float?,
    // Photometric
    val photometricCombinedScore: Float?,
    val specularScore: Float?,
    val chrominanceScore: Float?,
    val edgeDofScore: Float?,
    val lightingScore: Float?,
    // Temporal
    val headMovementVariance: Float?,
    val blinkDetected: Boolean?,
    val movementSmoothness: Float?,
    // Frame-level
    val frameSimilarity: Float,
    val faceSharpness: Float,
    val faceDetected: Boolean,
    val faceConfidence: Float?,
    val processingTimeMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("raw_status", rawStatus.name)
        put("stable_status", stableStatus.name)
        put("aggregated_score", aggregatedScore.toDouble())
        put("texture_genuine", textureGenuineScore?.toDouble() ?: JSONObject.NULL)
        put("texture_spoof", textureSpoofScore?.toDouble() ?: JSONObject.NULL)
        put("depth_score", depthScore?.toDouble() ?: JSONObject.NULL)
        put("mn3_real_score", mn3RealScore?.toDouble() ?: JSONObject.NULL)
        put("mn3_spoof_score", mn3SpoofScore?.toDouble() ?: JSONObject.NULL)
        put("cdcn_depth_score", cdcnDepthScore?.toDouble() ?: JSONObject.NULL)
        put("cdcn_triggered", cdcnTriggered ?: JSONObject.NULL)
        put("cdcn_available", cdcnAvailable ?: JSONObject.NULL)
        put("moire_score", moireScore?.toDouble() ?: JSONObject.NULL)
        put("lbp_screen_score", lbpScreenScore?.toDouble() ?: JSONObject.NULL)
        put("spectral_flatness", spectralFlatness?.toDouble() ?: JSONObject.NULL)
        put("device_detected", deviceDetected ?: JSONObject.NULL)
        put("device_spoof_score", deviceSpoofScore?.toDouble() ?: JSONObject.NULL)
        put("photometric_combined", photometricCombinedScore?.toDouble() ?: JSONObject.NULL)
        put("specular_score", specularScore?.toDouble() ?: JSONObject.NULL)
        put("chrominance_score", chrominanceScore?.toDouble() ?: JSONObject.NULL)
        put("edge_dof_score", edgeDofScore?.toDouble() ?: JSONObject.NULL)
        put("lighting_score", lightingScore?.toDouble() ?: JSONObject.NULL)
        put("head_movement_variance", headMovementVariance?.toDouble() ?: JSONObject.NULL)
        put("blink_detected", blinkDetected ?: JSONObject.NULL)
        put("movement_smoothness", movementSmoothness?.toDouble() ?: JSONObject.NULL)
        put("frame_similarity", frameSimilarity.toDouble())
        put("face_sharpness", faceSharpness.toDouble())
        put("face_detected", faceDetected)
        put("face_confidence", faceConfidence?.toDouble() ?: JSONObject.NULL)
        put("processing_time_ms", processingTimeMs)
    }
}
