package com.openpad.core.depth

import android.graphics.Bitmap
import com.openpad.core.PadConfig
import com.openpad.core.detection.FaceDetection
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

/**
 * Cascaded depth inference: MN3 (fast gate) → CDCN (deep analysis).
 *
 * Per-frame flow:
 * 1. Collect previous CDCN future (if ready, pipelined from prior frame)
 * 2. Run MN3 synchronously (~50ms) — always produces a score
 * 3. If MN3 real score >= gate threshold → fire CDCN async (bitmap copy)
 * 4. Else → skip CDCN (save compute)
 * 5. Return [DepthResult] with MN3 (always) + CDCN (if cached from prev frame)
 *
 * CDCN is pipelined: Frame N triggers CDCN, Frame N+1 receives its result.
 * This hides CDCN's ~1200ms latency behind the frame pipeline.
 */
class CascadedDepthAnalyzer(
    private val models: CdcnDepthAnalyzer,
    private val config: PadConfig
) : DepthAnalyzer {

    private val cdcnExecutor = Executors.newSingleThreadExecutor()
    private val pendingCdcn = AtomicReference<Future<CdcnInferenceResult>?>(null)
    private var cachedCdcnResult: CdcnInferenceResult? = null

    override fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): DepthResult {
        // Step 1: Collect previous CDCN result
        collectPendingCdcn()

        // Step 2: Run MN3 synchronously
        val (mn3Real, mn3Spoof) = models.analyzeMn3(bitmap, faceBbox)

        // Step 3: Gate decision
        val shouldTriggerCdcn = models.hasCdcn && mn3Real >= config.mn3GateThreshold

        if (shouldTriggerCdcn) {
            val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            val bboxCopy = faceBbox
            pendingCdcn.set(cdcnExecutor.submit<CdcnInferenceResult> {
                try {
                    models.analyzeCdcn(bitmapCopy, bboxCopy)
                } finally {
                    bitmapCopy.recycle()
                }
            })
        } else {
            pendingCdcn.getAndSet(null)?.cancel(false)
            cachedCdcnResult = null
        }

        // Step 4: Build unified result
        val cdcn = cachedCdcnResult
        return if (cdcn != null) {
            DepthResult.fromBoth(
                mn3Real = mn3Real,
                mn3Spoof = mn3Spoof,
                cdcnScore = cdcn.depthScore,
                cdcnVariance = cdcn.variance,
                cdcnMean = cdcn.rawMean,
                characteristics = cdcn.characteristics
            )
        } else {
            DepthResult.fromMn3Only(
                mn3Real = mn3Real,
                mn3Spoof = mn3Spoof,
                cdcnTriggered = shouldTriggerCdcn
            )
        }
    }

    private fun collectPendingCdcn() {
        val future = pendingCdcn.getAndSet(null) ?: return
        try {
            if (future.isDone) {
                cachedCdcnResult = future.get()
            } else {
                // Not done yet — put it back for next frame
                pendingCdcn.compareAndSet(null, future)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            cachedCdcnResult = null
        }
    }

    override fun close() {
        pendingCdcn.getAndSet(null)?.cancel(true)
        cdcnExecutor.shutdownNow()
        models.close()
    }

    companion object
}
