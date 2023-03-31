package com.vl.holdout.pull

import kotlin.math.abs
import kotlin.math.pow

class MovePullAnimator(
    private val weightHorizontal: Float,
    private val weightVertical: Float
): OnPullListener {
    var interpolator: (Float) -> Float =
        { x -> (x / 2 - (x / 2).pow(2)) * 2 } // x = [0 : 1] y = [0 : 0.5]

    override fun onPull(event: PullDispatcher.Event) {
        event.view.x = event.x + interpolate(event.dx, event.rangeHorizontal) / weightHorizontal
        event.view.y = event.y + interpolate(event.dy, event.rangeVertical) / weightVertical
    }

    private fun interpolate(x: Float, range: Float) =
        if (x == 0f)
            0f
        else
            interpolator(java.lang.Float.min(abs(x), range) / range) * range * x / abs(x)
}