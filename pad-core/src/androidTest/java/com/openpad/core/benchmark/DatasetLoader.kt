package com.openpad.core.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/**
 * Loads benchmark dataset from device storage.
 *
 * Expects a directory structure:
 * ```
 * baseDir/
 *   manifest.json
 *   replay-attack/test/
 *     real_client001_.../frame_000.jpg, frame_001.jpg, ...
 *     attack_fixed_.../frame_000.jpg, ...
 * ```
 */
class DatasetLoader(private val baseDir: File) {

    data class VideoSequence(
        val id: String,
        val label: String,
        val attackType: String?,
        val framesDir: File,
        val frameCount: Int
    )

    fun loadManifest(): List<VideoSequence> {
        val manifestFile = File(baseDir, "manifest.json")
        require(manifestFile.exists()) { "manifest.json not found in $baseDir" }

        val manifest = JSONObject(manifestFile.readText())
        val videos = manifest.getJSONArray("videos")

        return (0 until videos.length()).map { i ->
            val video = videos.getJSONObject(i)
            VideoSequence(
                id = video.getString("id"),
                label = video.getString("label"),
                attackType = if (video.isNull("attack_type")) null else video.getString("attack_type"),
                framesDir = File(baseDir, video.getString("frames_dir")),
                frameCount = video.getInt("frame_count")
            )
        }
    }

    /** Load sorted frame files for a video sequence. */
    fun getFrameFiles(video: VideoSequence): List<File> {
        val dir = video.framesDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension in listOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.nameWithoutExtension.substringAfterLast("_").toIntOrNull() ?: 0 }
            ?: emptyList()
    }

    /** Decode a frame file to Bitmap, downsampled to save memory. */
    fun loadFrame(file: File): Bitmap? {
        // First get dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        // Downsample if larger than 640px on any side
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        val sampleSize = if (maxDim > 640) maxDim / 640 else 1
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }
}
