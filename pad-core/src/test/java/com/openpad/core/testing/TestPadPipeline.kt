package com.openpad.core.testing

import android.graphics.Bitmap
import com.openpad.core.InternalPadConfig
import com.openpad.core.PadPipelineContract
import com.openpad.core.PadResult
import com.openpad.core.analyzer.PadFrameAnalyzer
import com.openpad.core.challenge.ChallengeEvidence
import com.openpad.core.challenge.ChallengeManager
import com.openpad.core.challenge.ChallengePhase
import com.openpad.core.detection.FaceDetection
import com.openpad.core.embedding.FaceEmbeddingAnalyzer
import com.openpad.core.embedding.FaceEmbeddingResult

/**
 * Test double for [PadPipelineContract].
 *
 * No real models are loaded. Provides controllable behavior for
 * ViewModel tests: frame results can be injected via [onResultCallback],
 * and the challenge manager returns configurable phases.
 */
class TestPadPipeline(
    override val config: InternalPadConfig = InternalPadConfig.Default,
    var nextPhase: ChallengePhase = ChallengePhase.ANALYZING
) : PadPipelineContract {

    var onResultCallback: ((PadResult) -> Unit)? = null
    var closeCalled = false
        private set

    override val embeddingAnalyzer: FaceEmbeddingAnalyzer = object : FaceEmbeddingAnalyzer {
        override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox) =
            FaceEmbeddingResult(FloatArray(128))

        override fun analyzePair(
            bitmapA: Bitmap, bboxA: FaceDetection.BBox,
            bitmapB: Bitmap, bboxB: FaceDetection.BBox
        ) = Pair(
            FaceEmbeddingResult(FloatArray(128)),
            FaceEmbeddingResult(FloatArray(128))
        )

        override fun cosineSimilarity(a: FloatArray, b: FloatArray): Float = 1.0f

        override fun close() {}
    }

    override fun createFrameAnalyzer(onResult: (PadResult) -> Unit): PadFrameAnalyzer {
        onResultCallback = onResult
        @Suppress("CAST_NEVER_SUCCEEDS")
        return null as PadFrameAnalyzer
    }

    override fun createChallengeManager(): ChallengeManager = TestChallengeManager()

    override fun close() {
        closeCalled = true
    }

    inner class TestChallengeManager : ChallengeManager {
        override var phase: ChallengePhase = ChallengePhase.IDLE
        override val evidence: ChallengeEvidence = ChallengeEvidence()

        override fun onFrame(result: PadResult): ChallengePhase {
            phase = nextPhase
            return nextPhase
        }

        override fun reset() {
            phase = ChallengePhase.IDLE
        }

        override fun advanceToLive() {
            phase = ChallengePhase.LIVE
        }

        override fun handleSpoof(): Boolean {
            phase = ChallengePhase.DONE
            return true
        }

        override fun advanceToDone() {
            phase = ChallengePhase.DONE
        }
    }
}
