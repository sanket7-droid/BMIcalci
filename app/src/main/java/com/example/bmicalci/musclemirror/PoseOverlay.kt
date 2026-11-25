package com.example.bmicalci.musclemirror

import android.graphics.Matrix
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/** MediaPipe 33-point pose topology (index pairs). */
val POSE_CONNECTIONS: List<Pair<Int, Int>> = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26, 25 to 27, 26 to 28,
    27 to 29, 28 to 30, 29 to 31, 30 to 32, 27 to 31, 28 to 32,
)

private const val MIN_VISIBILITY = 0.5f

/**
 * Draws the skeleton on top of [previewView]. Points are mapped from the analysis image to preview pixels with
 * CameraX's [CoordinateTransform], so FILL_CENTER cropping, rotation and front-camera mirroring come from the
 * real camera pipeline rather than hand-written math.
 */
@Composable
fun PoseOverlay(frame: PoseFrame?, previewView: PreviewView, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (frame == null || frame.landmarks.size < 33) return@Canvas
        val target = previewView.outputTransform ?: return@Canvas
        val matrix = Matrix()
        try {
            CoordinateTransform(frame.sourceTransform, target).transform(matrix)
        } catch (_: IllegalArgumentException) {
            return@Canvas
        }
        val pts = FloatArray(2)
        val mapped = frame.landmarks.map { lm ->
            pts[0] = lm.x * frame.imageWidth
            pts[1] = lm.y * frame.imageHeight
            matrix.mapPoints(pts)
            Offset(pts[0], pts[1])
        }
        val line = Color(0xFF00E5FF)
        for ((a, b) in POSE_CONNECTIONS) {
            if (frame.landmarks[a].visibility < MIN_VISIBILITY || frame.landmarks[b].visibility < MIN_VISIBILITY) continue
            drawLine(line, mapped[a], mapped[b], strokeWidth = 5f, cap = StrokeCap.Round)
        }
        mapped.forEachIndexed { i, p ->
            if (frame.landmarks[i].visibility >= MIN_VISIBILITY) drawCircle(Color.White, radius = 6f, center = p)
        }
    }
}
