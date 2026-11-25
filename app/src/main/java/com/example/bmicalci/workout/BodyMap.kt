package com.example.bmicalci.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.bmicalci.ui.theme.Neon

private data class Region(val muscle: Muscle, val x: Float, val y: Float, val w: Float, val h: Float)

private val frontRegions = listOf(
    Region(Muscle.SHOULDERS, 52f, 70f, 28f, 26f), Region(Muscle.SHOULDERS, 120f, 70f, 28f, 26f),
    Region(Muscle.CHEST, 80f, 74f, 19f, 28f), Region(Muscle.CHEST, 101f, 74f, 19f, 28f),
    Region(Muscle.BICEPS, 40f, 98f, 18f, 48f), Region(Muscle.BICEPS, 142f, 98f, 18f, 48f),
    Region(Muscle.ABS, 82f, 104f, 36f, 66f),
    Region(Muscle.QUADS, 68f, 178f, 30f, 92f), Region(Muscle.QUADS, 102f, 178f, 30f, 92f),
    Region(Muscle.CALVES, 70f, 276f, 26f, 86f), Region(Muscle.CALVES, 104f, 276f, 26f, 86f),
)

private val backRegions = listOf(
    Region(Muscle.SHOULDERS, 52f, 70f, 28f, 26f), Region(Muscle.SHOULDERS, 120f, 70f, 28f, 26f),
    Region(Muscle.BACK, 78f, 74f, 44f, 70f),
    Region(Muscle.TRICEPS, 40f, 98f, 18f, 48f), Region(Muscle.TRICEPS, 142f, 98f, 18f, 48f),
    Region(Muscle.GLUTES, 72f, 148f, 56f, 34f),
    Region(Muscle.HAMSTRINGS, 68f, 186f, 30f, 84f), Region(Muscle.HAMSTRINGS, 102f, 186f, 30f, 84f),
    Region(Muscle.CALVES, 70f, 276f, 26f, 86f), Region(Muscle.CALVES, 104f, 276f, 26f, 86f),
)

fun musclesOnSide(back: Boolean): Set<Muscle> = (if (back) backRegions else frontRegions).map { it.muscle }.toSet()

/** Heatmap body: green = recovered, red = fatigued. Tap a muscle to inspect it. */
@Composable
fun BodyMap(
    fatigue: Map<Muscle, Float>,
    selected: Muscle?,
    back: Boolean,
    onSelect: (Muscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val regions = if (back) backRegions else frontRegions
    Canvas(
        modifier = modifier
            .height(340.dp)
            .aspectRatio(0.5f)
            .semantics { contentDescription = "Muscle recovery map, ${if (back) "back" else "front"} view. Muscle list below gives the same details." }
            .pointerInput(back) {
                detectTapGestures { tap ->
                    val scale = size.width / 200f
                    val x = tap.x / scale
                    val y = tap.y / scale
                    regions.firstOrNull { x in it.x..(it.x + it.w) && y in it.y..(it.y + it.h) }?.let { onSelect(it.muscle) }
                }
            },
    ) {
        val scale = size.width / 200f
        // Head + neck + faint torso guide
        drawCircle(Neon.Border, radius = 20f * scale, center = Offset(100f * scale, 38f * scale), style = Stroke(2f * scale))
        drawRoundRect(
            Neon.Border, topLeft = Offset(70f * scale, 62f * scale), size = Size(60f * scale, 116f * scale),
            cornerRadius = CornerRadius(14f * scale), style = Stroke(1.5f * scale),
        )
        for (r in regions) {
            val f = (fatigue[r.muscle] ?: 0f).coerceIn(0f, 1f)
            val color = lerp(Neon.Green, Neon.Red, f)
            val topLeft = Offset(r.x * scale, r.y * scale)
            val sz = Size(r.w * scale, r.h * scale)
            drawRoundRect(color.copy(alpha = 0.85f), topLeft, sz, CornerRadius(8f * scale))
            if (r.muscle == selected) {
                drawRoundRect(Neon.Cyan, topLeft, sz, CornerRadius(8f * scale), style = Stroke(3f * scale))
            }
        }
    }
}
