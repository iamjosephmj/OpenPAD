package com.openpad.core

import com.openpad.core.analyzer.PadFrameAnalyzer
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.embedding.FaceEmbeddingAnalyzer

/**
 * Contract for the PAD pipeline. Allows test doubles to be
 * substituted for the real [PadPipeline] in ViewModel tests.
 */
internal interface PadPipelineContract {

    val config: InternalPadConfig

    val embeddingAnalyzer: FaceEmbeddingAnalyzer

    fun createFrameAnalyzer(onResult: (PadResult) -> Unit): PadFrameAnalyzer

    fun createChallengeManager(): ChallengeManager

    fun close()
}
