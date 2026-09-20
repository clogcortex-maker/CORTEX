package com.cortex.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ConditionOptions.ConditionType

/**
 * Thin wrapper around the MediaPipe ImageGenerator (on-device SD 1.5 via OpenCL GPU).
 * One generator instance is alive at a time; it is recreated when switching between
 * plain text-to-image and condition-image (refine) modes.
 */
class CortexEngine(private val context: Context) {

    private var generator: ImageGenerator? = null
    private var conditioned = false
    private var conditionKey = ""

    fun toConditionType(c: Condition): ConditionType = when (c) {
        Condition.EDGE -> ConditionType.EDGE
        Condition.DEPTH -> ConditionType.DEPTH
        Condition.FACE -> ConditionType.FACE
    }

    @Synchronized
    fun ensurePlain(modelDir: String) {
        if (generator != null && !conditioned) return
        closeQuietly()
        val options = ImageGenerator.ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelDir)
            .build()
        generator = ImageGenerator.createFromOptions(context, options)
        conditioned = false
        conditionKey = ""
    }

    @Synchronized
    fun ensureConditioned(modelDir: String, plugins: FileLocator) {
        val ready = Condition.values().filter { plugins.isPluginReady(it) }
        val key = ready.joinToString(",") { it.name }
        if (generator != null && conditioned && conditionKey == key) return
        closeQuietly()
        val b = ConditionOptions.builder()
        for (c in ready) {
            when (c) {
                Condition.EDGE -> b.setEdgeConditionOptions(
                    ConditionOptions.EdgeConditionOptions.builder()
                        .setPluginModelBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(plugins.pluginFile(c).absolutePath)
                                .build())
                        .build())
                Condition.DEPTH -> b.setDepthConditionOptions(
                    ConditionOptions.DepthConditionOptions.builder()
                        .setDepthModelBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(plugins.conditionModelFile(c).absolutePath)
                                .build())
                        .setPluginModelBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(plugins.pluginFile(c).absolutePath)
                                .build())
                        .build())
                Condition.FACE -> b.setFaceConditionOptions(
                    ConditionOptions.FaceConditionOptions.builder()
                        .setFaceModelBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(plugins.conditionModelFile(c).absolutePath)
                                .build())
                        .setPluginModelBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(plugins.pluginFile(c).absolutePath)
                                .build())
                        .build())
            }
        }
        val options = ImageGenerator.ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelDir)
            .build()
        generator = ImageGenerator.createFromOptions(context, options, b.build())
        conditioned = true
        conditionKey = key
    }

    @Synchronized
    fun setInputs(prompt: String, iterations: Int, seed: Int) {
        generator!!.setInputs(prompt, iterations, seed)
    }

    @Synchronized
    fun setConditionedInputs(prompt: String, image: Bitmap, type: ConditionType, iterations: Int, seed: Int) {
        val mp = BitmapImageBuilder(image).build()
        generator!!.setInputs(prompt, mp, type, iterations, seed)
    }

    /** One denoising step. Returns a preview bitmap on display steps (or the final image). */
    @Synchronized
    fun execute(showResult: Boolean): Bitmap? {
        val result = generator!!.execute(showResult) ?: return null
        return BitmapExtractor.extract(result.generatedImage())
    }

    @Synchronized
    fun closeQuietly() {
        try {
            generator?.close()
        } catch (_: Throwable) {
        }
        generator = null
    }

    /** File access helpers decoupled so tests / callers can supply their own locator. */
    interface FileLocator {
        fun isPluginReady(c: Condition): Boolean
        fun pluginFile(c: Condition): java.io.File
        fun conditionModelFile(c: Condition): java.io.File
    }
}
