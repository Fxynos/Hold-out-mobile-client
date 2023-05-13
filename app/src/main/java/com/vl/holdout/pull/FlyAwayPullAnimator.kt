package com.vl.holdout.pull

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Must not be used to handle events of several PullDispatcher's
 * @see PullDispatcher
 */
class FlyAwayPullAnimator(
    private val flyAwayDuration: Long,
    private val destinationOffsetX: Float,
    private val destinationOffsetDegree: Float,
    private val returnDuration: Long,
    private val returnOffsetY: Float,
    private val animationEndCallback: ((animationCode: Int)->Unit)? = null
): OnPullListener, Lock {
    companion object {
        const val ANIMATION_FLEW_AWAY = 0
        const val ANIMATION_ARRIVED = 1
    }

    private var isLastAnimationStep = false

    override fun onPull(event: PullDispatcher.Event) {
        event.lock(this)
        val x = event.view.x
        val angle = event.view.rotation
        val leftDirection = event.dx < 0
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = flyAwayDuration
        animator.interpolator = AccelerateInterpolator()
        animator.addUpdateListener {
            if (!isLastAnimationStep) { // fly away animation
                event.view.x = x + animator.animatedValue as Float * destinationOffsetX *
                        if (leftDirection) -1 else 1
                event.view.rotation =
                    angle + animator.animatedValue as Float * destinationOffsetDegree *
                            if (leftDirection) -1 else 1
            } else // return animation
                event.view.y = event.y + returnOffsetY *
                        (animator.animatedValue as Float - 1)
        }
        animator.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) =
                if (isLastAnimationStep) {
                    isLastAnimationStep = false
                    animationEndCallback?.invoke(ANIMATION_ARRIVED)
                    event.unlock(this@FlyAwayPullAnimator).let {}
                } else {
                    isLastAnimationStep = true
                    animationEndCallback?.invoke(ANIMATION_FLEW_AWAY)
                    event.view.rotation = 0f
                    event.view.x = event.x
                    event.view.y = event.y - returnOffsetY
                    animator.interpolator = DecelerateInterpolator()
                    animator.duration = returnDuration
                    animator.start()
                }
        })
        animator.start()
    }
}