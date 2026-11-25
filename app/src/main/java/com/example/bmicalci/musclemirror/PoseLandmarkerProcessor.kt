package com.example.bmicalci.musclemirror

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which bundled model to run. Only Lite ships in assets for now; drop the Full .task there to enable it. */
enum class PoseModel(val assetName: String) {
    Lite("pose_landmarker_lite.task"),
    Full("pose_landmarker_full.task"),
}

data class Landmark(val x: Float, val y: Float, val visibility: Float, val presence: Float)

/**
 * One detection result. [landmarks] are normalized (0..1) in the UPRIGHT analysis image; [sourceTransform]
 * describes that image's relation to the sensor so the overlay can map to the preview with CameraX's own
 * CoordinateTransform (rotation, crop and front-camera mirroring included).
 */
data class PoseFrame(
    val landmarks: List<Landmark>,
    val imageWidth: Int,
    val imageHeight: Int,
    val sourceTransform: OutputTransform,
    val inferenceMs: Long,
)

/** CameraX analyzer that feeds MediaPipe Pose Landmarker in LIVE_STREAM mode. */
class PoseLandmarkerProcessor(
    context: Context,
    private val model: PoseModel = PoseModel.Lite,
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext
    private val transformFactory = ImageProxyTransformFactory().apply {
        isUsingRotationDegrees = true
        isUsingCropRect = true
    }

    private val _frames = MutableStateFlow<PoseFrame?>(null)
    val frames: StateFlow<PoseFrame?> = _frames.asStateFlow()

    private val _info = MutableStateFlow("starting")
    val info: StateFlow<String> = _info.asStateFlow()

    @Volatile private var landmarker: PoseLandmarker? = null
    @Volatile private var closed = false
    private var initFailed = false
    private var lastTimestampMs = 0L

    // Per-frame state handed from analyzer thread to the result listener (same order, LIVE_STREAM is sequential).
    @Volatile private var pendingTransform: OutputTransform? = null
    @Volatile private var pendingW = 0
    @Volatile private var pendingH = 0
    @Volatile private var pendingStartNs = 0L

    // Called on the analysis executor thread.
    override fun analyze(image: ImageProxy) {
        try {
            if (closed) return
            val lm = landmarker ?: createLandmarker()?.also { landmarker = it }
            if (lm == null) return

            val rotation = image.imageInfo.rotationDegrees
            val upright = rotation == 0 || rotation == 180
            pendingTransform = transformFactory.getOutputTransform(image)
            pendingW = if (upright) image.width else image.height
            pendingH = if (upright) image.height else image.width
            pendingStartNs = SystemClock.elapsedRealtimeNanos()

            // toBitmap() is an independent copy in sensor orientation. Rotate it upright ourselves (clockwise by
            // rotationDegrees) so landmarks come back in the same upright space the overlay transform expects.
            val raw = image.toBitmap()
            val upBitmap = if (rotation == 0) raw else {
                val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
            }
            val mpImage: MPImage = BitmapImageBuilder(upBitmap).build()

            // LIVE_STREAM requires strictly increasing timestamps.
            var ts = SystemClock.uptimeMillis()
            if (ts <= lastTimestampMs) ts = lastTimestampMs + 1
            lastTimestampMs = ts

            try {
                lm.detectAsync(mpImage, ts)
            } finally {
                mpImage.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "analyze failed", t)
            _info.value = "error: ${t.message}"
        } finally {
            image.close()
        }
    }

    private fun createLandmarker(): PoseLandmarker? {
        if (initFailed) return null
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val base = BaseOptions.builder()
                    .setModelAssetPath(model.assetName)
                    .setDelegate(delegate)
                    .build()
                val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ -> onResult(result) }
                    .setErrorListener { e -> Log.e(TAG, "MediaPipe error", e); _info.value = "error: ${e.message}" }
                    .build()
                val created = PoseLandmarker.createFromOptions(appContext, opts)
                _info.value = "${model.name} / $delegate"
                Log.i(TAG, "PoseLandmarker ready: ${model.name} on $delegate")
                return created
            } catch (t: Throwable) {
                Log.w(TAG, "PoseLandmarker init failed on $delegate", t)
            }
        }
        initFailed = true
        _info.value = "model load failed"
        return null
    }

    // Called on a MediaPipe thread.
    private fun onResult(result: PoseLandmarkerResult) {
        val transform = pendingTransform ?: return
        val ms = (SystemClock.elapsedRealtimeNanos() - pendingStartNs) / 1_000_000
        val first = result.landmarks().firstOrNull()
        val list = first?.map {
            Landmark(
                x = it.x(),
                y = it.y(),
                visibility = it.visibility().orElse(0f),
                presence = it.presence().orElse(0f),
            )
        }.orEmpty()
        _frames.value = PoseFrame(list, pendingW, pendingH, transform, ms)
    }

    /** Releases native resources. Safe to call more than once. */
    fun close() {
        closed = true
        landmarker?.close()
        landmarker = null
    }

    private companion object {
        const val TAG = "PoseLandmarker"
    }
}
