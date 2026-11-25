package com.example.bmicalci.musclemirror

import kotlin.math.PI
import kotlin.math.abs

/** One-Euro filter for a single scalar: low lag on fast motion, strong smoothing when still. */
private class OneEuro(private val minCutoff: Float, private val beta: Float, private val dCutoff: Float) {
    private var xPrev = 0f
    private var dxPrev = 0f
    private var init = false

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(x: Float, dt: Float): Float {
        if (!init) {
            init = true; xPrev = x; dxPrev = 0f; return x
        }
        val d = (x - xPrev) / dt
        val dHat = dxPrev + alpha(dCutoff, dt) * (d - dxPrev)
        val cutoff = minCutoff + beta * abs(dHat)
        val xHat = xPrev + alpha(cutoff, dt) * (x - xPrev)
        xPrev = xHat; dxPrev = dHat
        return xHat
    }
}

/** Smooths the 33 landmarks (x, y; visibility/presence pass through). Call [reset] when the person is lost. */
class LandmarkSmoother(
    minCutoff: Float = 1.5f,
    beta: Float = 8f,
    dCutoff: Float = 1f,
) {
    private val fx = List(33) { OneEuro(minCutoff, beta, dCutoff) }
    private val fy = List(33) { OneEuro(minCutoff, beta, dCutoff) }
    private var lastNs = 0L
    private val make = { OneEuro(minCutoff, beta, dCutoff) }
    private var xs = fx
    private var ys = fy

    fun smooth(landmarks: List<Landmark>, timestampNs: Long): List<Landmark> {
        if (landmarks.size < 33) return landmarks
        val dt = if (lastNs == 0L) 1f / 30f else ((timestampNs - lastNs) / 1e9f).coerceIn(0.005f, 0.2f)
        lastNs = timestampNs
        return landmarks.mapIndexed { i, l -> l.copy(x = xs[i].filter(l.x, dt), y = ys[i].filter(l.y, dt)) }
    }

    fun reset() {
        lastNs = 0L
        xs = List(33) { make() }
        ys = List(33) { make() }
    }
}
