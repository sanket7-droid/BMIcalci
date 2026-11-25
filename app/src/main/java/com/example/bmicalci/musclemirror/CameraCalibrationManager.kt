package com.example.bmicalci.musclemirror

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class CalibrationState { NO_PERSON, TOO_CLOSE, PARTIAL_BODY, ADJUST_ANGLE, READY }

/**
 * Approximate camera-position check from 2D landmarks only (no depth), so thresholds are heuristics.
 * A new state must persist for [stableFrames] consecutive frames before it is reported, to stop flicker.
 */
class CameraCalibrationManager(
    private val spec: ExerciseCameraSpec = ExerciseCameraSpec.Squat,
    private val minVisibility: Float = 0.5f,
    private val edgeMargin: Float = 0.02f,
    private val stableFrames: Int = 5,
) {
    private var reported = CalibrationState.NO_PERSON
    private var candidate = CalibrationState.NO_PERSON
    private var candidateCount = 0

    fun update(landmarks: List<Landmark>): CalibrationState {
        val raw = evaluate(landmarks)
        if (raw == reported) {
            candidate = raw; candidateCount = 0
        } else if (raw == candidate) {
            if (++candidateCount >= stableFrames) { reported = raw; candidateCount = 0 }
        } else {
            candidate = raw; candidateCount = 1
        }
        return reported
    }

    fun reset() { reported = CalibrationState.NO_PERSON; candidate = reported; candidateCount = 0 }

    private fun evaluate(lm: List<Landmark>): CalibrationState {
        if (lm.size < 33) return CalibrationState.NO_PERSON
        val req = spec.requiredLandmarks.map { lm[it] }
        // Nothing meaningful visible at all.
        if (lm.count { it.visibility >= minVisibility } < 5) return CalibrationState.NO_PERSON

        val top = lm.minOf { it.y }
        val bottom = lm.maxOf { it.y }
        val clippedEdge = lm.any { it.visibility >= minVisibility && (it.y < edgeMargin || it.y > 1 - edgeMargin) }
        val missing = req.any { it.visibility < minVisibility || it.y !in 0f..1f || it.x !in 0f..1f }
        val bodyHeight = bottom - top

        if (missing) {
            // Body fills the frame yet legs/head are cut off -> too close; otherwise just not fully in view / too far.
            return if (clippedEdge || bodyHeight > 0.95f) CalibrationState.TOO_CLOSE else CalibrationState.PARTIAL_BODY
        }
        if (bodyHeight < 0.4f) return CalibrationState.PARTIAL_BODY // too far / too small to track well

        val shoulderW = hypot(lm[11].x - lm[12].x, lm[11].y - lm[12].y)
        val torso = hypot(
            (lm[11].x + lm[12].x) / 2 - (lm[23].x + lm[24].x) / 2,
            (lm[11].y + lm[12].y) / 2 - (lm[23].y + lm[24].y) / 2,
        )
        if (torso < 1e-3f) return CalibrationState.PARTIAL_BODY
        val ratio = shoulderW / torso
        val ok = ratio >= spec.frontRatioMin || ratio <= spec.sideRatioMax
        return if (ok) CalibrationState.READY else CalibrationState.ADJUST_ANGLE
    }
}
