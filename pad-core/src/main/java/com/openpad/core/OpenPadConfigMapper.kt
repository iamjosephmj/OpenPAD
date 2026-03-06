package com.openpad.core

/**
 * Maps the public [OpenPadConfig] to the internal [InternalPadConfig].
 *
 * Field mapping:
 * - livenessThreshold -> genuineProbabilityThreshold
 * - faceMatchThreshold -> faceConsistencyThreshold
 * - faceDetectionConfidence -> minFaceConfidence
 * - textureAnalysisWeight -> textureWeight
 * - depthGateWeight -> mn3Weight
 * - depthAnalysisWeight -> cdcnWeight
 * - depthGateMinScore -> mn3GateThreshold
 * - depthFlatnessMinScore -> depthFlatnessThreshold
 * - moireDetectionThreshold -> moireThreshold
 * - screenPatternThreshold -> lbpScreenThreshold
 * - spoofAttemptPenalty -> spoofAttemptPenaltyPerCount
 * - maxFramesPerSecond -> maxFps
 * - sessionTimeoutMs -> sessionTimeoutMs
 * - challengeTimeoutMs -> challengeTimeoutMs
 */
internal object OpenPadConfigMapper {

    fun toInternal(config: OpenPadConfig): InternalPadConfig = InternalPadConfig(
        maxSpoofAttempts = config.maxSpoofAttempts,
        genuineProbabilityThreshold = config.livenessThreshold,
        faceConsistencyThreshold = config.faceMatchThreshold,
        minFaceConfidence = config.faceDetectionConfidence,
        textureWeight = config.textureAnalysisWeight,
        mn3Weight = config.depthGateWeight,
        cdcnWeight = config.depthAnalysisWeight,
        mn3GateThreshold = config.depthGateMinScore,
        depthFlatnessThreshold = config.depthFlatnessMinScore,
        moireThreshold = config.moireDetectionThreshold,
        lbpScreenThreshold = config.screenPatternThreshold,
        photometricMinScore = config.photometricMinScore,
        spoofAttemptPenaltyPerCount = config.spoofAttemptPenalty,
        maxFps = config.maxFramesPerSecond,
        enableFrameEnhancement = config.enableFrameEnhancement,
        staticFrameThreshold = config.staticFrameThreshold,
        minMotionVariance = config.minMotionVariance,
        lowLightThreshold = config.lowLightThreshold,
        lowLightRelaxation = config.lowLightRelaxation,
        screenReflectionConfidenceThreshold = config.screenReflectionMinConfidence,
        screenReflectionMinSignals = config.screenReflectionMinSignals,
        screenReflectionWeight = config.screenReflectionWeight,
        sessionTimeoutMs = config.sessionTimeoutMs,
        challengeTimeoutMs = config.challengeTimeoutMs,
        enablePreprocessing = config.enablePreprocessing,
        preprocessingGammaTarget = config.preprocessingGammaTarget,
        preprocessingClaheClipLimit = config.preprocessingClaheClipLimit
    )
}
