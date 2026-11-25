package com.example.bmicalci.musclemirror

/** What the camera must see for an exercise, and the coaching text per calibration state (English). */
data class ExerciseCameraSpec(
    val name: String,
    /** Landmark indices that must be visible and inside the frame. */
    val requiredLandmarks: List<Int>,
    /** Shoulder-width / torso-length ratio at or above which the body counts as facing the camera. */
    val frontRatioMin: Float,
    /** ...and at or below which it counts as side-on. Between the two = awkward diagonal. */
    val sideRatioMax: Float,
    val setupHint: String,
    val guidance: Map<CalibrationState, String>,
) {
    companion object {
        // 11/12 shoulders, 23/24 hips, 25/26 knees, 27/28 ankles
        val Squat = ExerciseCameraSpec(
            name = "Squat",
            requiredLandmarks = listOf(11, 12, 23, 24, 25, 26, 27, 28),
            frontRatioMin = 0.65f,
            sideRatioMax = 0.35f,
            setupHint = "Place the phone 2-3 m away at waist height. Stand facing the camera or fully side-on.",
            guidance = mapOf(
                CalibrationState.NO_PERSON to "No person detected. Step in front of the camera.",
                CalibrationState.TOO_CLOSE to "Too close. Step back a little.",
                CalibrationState.PARTIAL_BODY to "Get your full body (head to feet) in frame.",
                CalibrationState.ADJUST_ANGLE to "Angled view. Face the camera or turn fully sideways.",
                CalibrationState.READY to "Ready! Start your squat.",
            ),
        )
    }
}
