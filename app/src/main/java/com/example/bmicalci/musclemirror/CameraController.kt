package com.example.bmicalci.musclemirror

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the CameraX use cases (Preview + optional ImageAnalysis) for the front selfie camera.
 *
 * Analysis runs on its own single-thread executor and always uses KEEP_ONLY_LATEST backpressure, so a slow
 * detector can never queue frames up or stall the preview.
 */
class CameraController(context: Context) {
    private val appContext = context.applicationContext
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null

    /** The live analysis use case (null until [bind] ran with an analyzer). Exposes imageInfo/rotation to callers. */
    var imageAnalysis: ImageAnalysis? = null
        private set

    /**
     * Binds Preview (+ analysis when [analyzer] != null) to [owner]. Safe to call again: previous use cases are
     * unbound first. [onBound] / [onError] are delivered on the main thread.
     */
    fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer? = null,
        onBound: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                cameraProvider.unbindAll()

                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)
                if (analyzer != null) {
                    val analysis = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }
                    imageAnalysis = analysis
                    useCases += analysis
                } else {
                    imageAnalysis = null
                }

                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *useCases.toTypedArray())
                onBound()
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun unbind() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        provider?.unbindAll()
    }

    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }
}
