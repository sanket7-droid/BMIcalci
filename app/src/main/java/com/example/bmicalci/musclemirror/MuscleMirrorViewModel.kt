package com.example.bmicalci.musclemirror

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MirrorUiState(
    /** Smoothed frame ready for the overlay (null until the first result). */
    val frame: PoseFrame? = null,
    val calibration: CalibrationState = CalibrationState.NO_PERSON,
    val fps: Int = 0,
    val guidance: String = "",
)

/** Turns raw pose frames into UI state: smoothing, calibration, FPS. No camera or MediaPipe objects live here. */
class MuscleMirrorViewModel : ViewModel() {
    val spec: ExerciseCameraSpec = ExerciseCameraSpec.Squat
    private val smoother = LandmarkSmoother()
    private val calibration = CameraCalibrationManager(spec)

    private val _state = MutableStateFlow(MirrorUiState(guidance = spec.guidance.getValue(CalibrationState.NO_PERSON)))
    val state: StateFlow<MirrorUiState> = _state.asStateFlow()

    private var windowStartNs = 0L
    private var windowFrames = 0
    private var fps = 0

    fun onFrame(raw: PoseFrame?) {
        if (raw == null) return
        val now = SystemClock.elapsedRealtimeNanos()
        windowFrames++
        if (windowStartNs == 0L) windowStartNs = now
        if (now - windowStartNs >= 1_000_000_000L) {
            fps = windowFrames; windowFrames = 0; windowStartNs = now
        }

        val shown: PoseFrame
        val calib: CalibrationState
        if (raw.landmarks.isEmpty()) {
            smoother.reset()
            shown = raw
            calib = calibration.update(emptyList())
        } else {
            val smooth = smoother.smooth(raw.landmarks, now)
            shown = raw.copy(landmarks = smooth)
            calib = calibration.update(smooth)
        }
        _state.value = MirrorUiState(shown, calib, fps, spec.guidance.getValue(calib))
    }

    fun onCameraStopped() {
        smoother.reset(); calibration.reset()
        windowStartNs = 0L; windowFrames = 0; fps = 0
        _state.value = MirrorUiState(guidance = spec.guidance.getValue(CalibrationState.NO_PERSON))
    }
}
