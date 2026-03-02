package com.openpad.core.challenge

import com.openpad.core.PadConfig
import com.openpad.core.PadResult
import com.openpad.core.aggregation.PadStatus
import com.openpad.core.detection.FaceDetection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MovementChallengeTest {

    private lateinit var challenge: MovementChallenge

    @Before
    fun setup() {
        challenge = MovementChallenge(PadConfig.Default)
    }

    private fun padResult(
        status: PadStatus = PadStatus.LIVE,
        face: FaceDetection? = null,
        faceCropBitmap: android.graphics.Bitmap? = null
    ): PadResult = PadResult(
        status = status,
        faceDetection = face,
        rawFaceDetection = face,
        textureResult = null,
        depthResult = null,
        frequencyResult = null,
        deviceDetectionResult = null,
        photometricResult = null,
        faceCropBitmap = faceCropBitmap,
        temporalFeatures = null,
        aggregatedScore = 0.8f,
        frameSimilarity = 0.9f,
        faceSharpness = 0f,
        timestampMs = 0L
    )

    private fun face(centerX: Float = 0.5f, centerY: Float = 0.5f, area: Float = 0.1f): FaceDetection {
        val side = kotlin.math.sqrt(area)
        val left = centerX - side / 2
        val top = centerY - side / 2
        return FaceDetection(
            confidence = 0.9f,
            bbox = FaceDetection.BBox(left, top, left + side, top + side)
        )
    }

    @Test
    fun initialPhaseIsIdle() {
        assertEquals(ChallengePhase.IDLE, challenge.phase)
    }

    @Test
    fun firstFrameTransitionsToAnalyzing() {
        challenge.onFrame(padResult(face = null))
        assertEquals(ChallengePhase.ANALYZING, challenge.phase)
    }

    @Test
    fun analyzingWithFaceStableFramesTransitionsToChallengeCloser() {
        val config = PadConfig(analyzingStableFrames = 2)
        val ch = MovementChallenge(config)
        val f = face(area = 0.1f)

        ch.onFrame(padResult(face = f))
        assertEquals(ChallengePhase.ANALYZING, ch.phase)

        ch.onFrame(padResult(face = f))
        assertEquals(ChallengePhase.CHALLENGE_CLOSER, ch.phase)
    }

    @Test
    fun advanceToLiveUpdatesPhase() {
        challenge.onFrame(padResult(face = null))
        challenge.advanceToLive()
        assertEquals(ChallengePhase.LIVE, challenge.phase)
    }

    @Test
    fun advanceToDoneUpdatesPhase() {
        challenge.advanceToDone()
        assertEquals(ChallengePhase.DONE, challenge.phase)
    }

    @Test
    fun handleSpoofResetsToAnalyzing() {
        challenge.onFrame(padResult(face = null))
        challenge.advanceToLive()
        challenge.handleSpoof()
        assertEquals(ChallengePhase.ANALYZING, challenge.phase)
    }

    @Test
    fun resetReturnsToIdle() {
        challenge.onFrame(padResult(face = null))
        challenge.reset()
        assertEquals(ChallengePhase.IDLE, challenge.phase)
    }
}
