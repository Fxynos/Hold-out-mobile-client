package com.vl.holdout.pull

class RotatePullAnimator(private val degree: Float): OnPullListener {
    override fun onPull(event: PullDispatcher.Event) {
        event.view.rotation = if (event.dx == 0f) 0f else
            degree * event.dx / event.rangeHorizontal
    }
}