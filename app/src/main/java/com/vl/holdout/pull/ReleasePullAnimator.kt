package com.vl.holdout.pull

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * This class disables PullDispatcher while animation is processing.
 * It must be used only for release event.
 */
class ReleasePullAnimator(private val duration: Long): OnPullListener, Lock {
    override fun onPull(event: PullDispatcher.Event) {
        event.lock(this)
        val (x, y) = event.view.x to event.view.y
        val angle = event.view.rotation
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            event.view.x = animator.animatedValue as Float * (event.x - x) + x
            event.view.y = animator.animatedValue as Float * (event.y - y) + y
            event.view.rotation = (1 - animator.animatedValue as Float) * angle
        }
        animator.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) =
                event.unlock(this@ReleasePullAnimator).let {}
        })
        animator.start()
    }
}