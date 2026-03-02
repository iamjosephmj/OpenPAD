package com.openpad.core.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/** Utility for loading TFLite models from packed (.pad) assets. */
object ModelLoader {

    // Must match XOR_KEY in scripts/pack_models.py exactly
    private val XOR_KEY = byteArrayOf(
        0x4F, 0x70, 0x65, 0x6E, 0x50, 0x41, 0x44, 0x5F,
        0x4D, 0x6F, 0x64, 0x65, 0x6C, 0x47, 0x75, 0x61,
        0x72, 0x64, 0x5F, 0x32, 0x30, 0x32, 0x36, 0x5F,
        0x53, 0x44, 0x4B, 0x5F, 0x76, 0x31, 0x2E, 0x30,
    )

    /** Load a TFLite model from a packed .pad asset (XOR-scrambled gzip). */
    fun loadFromAssets(context: Context, modelPath: String): ByteBuffer {
        val scrambled = context.assets.open(modelPath).use { it.readBytes() }
        val compressed = xor(scrambled)
        val raw = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }

        return ByteBuffer.allocateDirect(raw.size).apply {
            order(ByteOrder.nativeOrder())
            put(raw)
            rewind()
        }
    }

    private fun xor(data: ByteArray): ByteArray {
        val keyLen = XOR_KEY.size
        return ByteArray(data.size) { i ->
            (data[i].toInt() xor XOR_KEY[i % keyLen].toInt()).toByte()
        }
    }

    /**
     * Create optimized [Interpreter.Options] with multi-threading and optional GPU.
     *
     * @param threads Number of CPU threads (default 4).
     * @param useGpu Whether to attempt GPU delegate (falls back to CPU on failure).
     */
    fun createOptions(threads: Int = 4, useGpu: Boolean = true): Interpreter.Options {
        val options = Interpreter.Options().apply {
            numThreads = threads
        }
        if (useGpu) {
            try {
                val compat = CompatibilityList()
                if (compat.isDelegateSupportedOnThisDevice) {
                    options.addDelegate(GpuDelegate())
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // GPU delegate unavailable; CPU fallback
            }
        }
        return options
    }
}
