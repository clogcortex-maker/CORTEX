package com.cortex.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

enum class Condition(val wire: String) {
    EDGE("EDGE"), DEPTH("DEPTH"), FACE("FACE")
}

/** Downloads and manages on-device model files (SD 1.5 + MediaPipe plugins). */
class ModelManager(private val context: Context) : CortexEngine.FileLocator {

    companion object {
        const val BASE_ZIP_URL =
            "https://huggingface.co/na5h13/stable-diffusion-v1-5-mediapipe/resolve/main/sd15-mediapipe.zip"
        const val EDGE_PLUGIN_URL =
            "https://storage.googleapis.com/mediapipe-models/image_generator/plugin_models/float32/latest/canny_edge_plugin.tflite"
        const val DEPTH_PLUGIN_URL =
            "https://storage.googleapis.com/mediapipe-models/image_generator/plugin_models/float32/latest/depth_plugin.tflite"
        const val DEPTH_MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/image_generator/condition_image_models/float16/latest/depth_512_512_fp16_opt_w_metadata.tflite"
        const val FACE_PLUGIN_URL =
            "https://storage.googleapis.com/mediapipe-models/image_generator/plugin_models/float32/latest/face_landmark_plugin.tflite"
        const val FACE_MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
    }

    val modelDir: File get() = File(context.filesDir, "sd15")
    private val pluginDir: File get() = File(context.filesDir, "plugins").apply { mkdirs() }
    val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun isBaseReady(): Boolean =
        File(modelDir, "bpe_simple_vocab_16e6.txt").exists() &&
        File(modelDir, "model.diffusion_model.input_blocks.0.0.weight.bin").exists() &&
        File(modelDir, "cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.k_proj.bias.bin").exists() &&
        File(modelDir, "first_stage_model.decoder.conv_in.weight.bin").exists()

    override fun isPluginReady(c: Condition): Boolean = when (c) {
        Condition.EDGE -> File(pluginDir, "canny_edge_plugin.tflite").length() > 40_000_000L
        Condition.DEPTH -> File(pluginDir, "depth_plugin.tflite").length() > 40_000_000L &&
                File(pluginDir, "depth_512_512_fp16_opt_w_metadata.tflite").length() > 20_000_000L
        Condition.FACE -> File(pluginDir, "face_landmark_plugin.tflite").length() > 40_000_000L &&
                File(pluginDir, "face_landmarker.task").length() > 3_000_000L
    }

    fun readyPlugins(): Set<Condition> = Condition.values().filter { isPluginReady(it) }.toSet()

    override fun pluginFile(c: Condition): File = when (c) {
        Condition.EDGE -> File(pluginDir, "canny_edge_plugin.tflite")
        Condition.DEPTH -> File(pluginDir, "depth_plugin.tflite")
        Condition.FACE -> File(pluginDir, "face_landmark_plugin.tflite")
    }

    override fun conditionModelFile(c: Condition): File = when (c) {
        Condition.EDGE -> File(pluginDir, "canny_edge_plugin.tflite")
        Condition.DEPTH -> File(pluginDir, "depth_512_512_fp16_opt_w_metadata.tflite")
        Condition.FACE -> File(pluginDir, "face_landmarker.task")
    }

    /** Full setup: base model zip (~2 GB) + edge refine plugin. */
    suspend fun downloadBase(onProgress: (stage: String, pct: Int) -> Unit) = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "sd15-mediapipe.zip")
        try {
            if (!isBaseReady()) {
                downloadToFile(BASE_ZIP_URL, zip) { read, total ->
                    val pct = if (total > 0) (read * 90 / total).toInt() else 0
                    onProgress("download", pct)
                }
                onProgress("unzip", 90)
                extractZip(zip, modelDir) { done, total ->
                    onProgress("unzip", 90 + (10 * done / total.coerceAtLeast(1)))
                }
            }
            if (!isPluginReady(Condition.EDGE)) {
                onProgress("plugins", 0)
                downloadToFile(EDGE_PLUGIN_URL, pluginFile(Condition.EDGE)) { _, _ -> }
            }
            onProgress("done", 100)
        } finally {
            zip.delete()
        }
    }

    /** Downloads the plugin files needed for a condition type, on demand. */
    suspend fun ensurePlugin(c: Condition, onProgress: (pct: Int) -> Unit) = withContext(Dispatchers.IO) {
        val files = mutableListOf<Pair<String, File>>()
        when (c) {
            Condition.EDGE -> files.add(EDGE_PLUGIN_URL to pluginFile(Condition.EDGE))
            Condition.DEPTH -> {
                files.add(DEPTH_PLUGIN_URL to pluginFile(Condition.DEPTH))
                files.add(DEPTH_MODEL_URL to conditionModelFile(Condition.DEPTH))
            }
            Condition.FACE -> {
                files.add(FACE_PLUGIN_URL to pluginFile(Condition.FACE))
                files.add(FACE_MODEL_URL to conditionModelFile(Condition.FACE))
            }
        }
        if (isPluginReady(c)) { onProgress(100); return@withContext }
        files.forEachIndexed { i, (url, dest) ->
            if (!dest.exists() || dest.length() < 1_000_000L) {
                downloadToFile(url, dest) { _, _ -> }
            }
            onProgress((100 * (i + 1) / files.size).coerceAtMost(100))
        }
    }

    fun deleteAll() {
        modelDir.deleteRecursively()
        File(context.filesDir, "plugins").deleteRecursively()
        File(context.cacheDir, "sd15-mediapipe.zip").delete()
    }

    private fun downloadToFile(urlString: String, dest: File, onProgress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.inputStream().use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractZip(zip: File, destDir: File, onProgress: (Int, Int) -> Unit) {
        val total: Int
        val names: List<String>
        ZipFile(zip).use { zf ->
            names = zf.entries().asSequence().map { it.name }.filter { !it.endsWith("/") }.toList()
            total = names.size
        }
        var done = 0
        ZipFile(zip).use { zf ->
            val buf = ByteArray(64 * 1024)
            for (name in names) {
                if (name.contains("..")) continue
                val out = File(destDir, name)
                out.parentFile?.mkdirs()
                zf.getInputStream(zf.getEntry(name)).use { input ->
                    FileOutputStream(out).use { fos ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                        }
                    }
                }
                done++
                if (done % 10 == 0 || done == total) onProgress(done, total)
            }
        }
    }
}
