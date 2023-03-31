package com.vl.holdout.pull

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import java.util.*
import kotlin.collections.HashSet


class PullDispatcher(
    private val eventSource: View,
    private val view: View,
    var rangeHorizontal: Float,
    var rangeVertical: Float
) {
    companion object {
        private const val ACTION_PULL = 1
        private const val ACTION_RELEASE = 2
    }

    val enabled
        get() = locks.isEmpty()

    // maybe should translation be used instead of raw coordinates
    private lateinit var originPosition: PointF
    private lateinit var touchOrigin: PointF

    private val pullListeners = HashSet<OnPullListener>()
    private val releaseListeners = HashSet<OnPullListener>()
    private val locks = HashSet<Lock>()

    init {
        eventSource.setOnTouchListener(TouchHandler())
    }

    fun addOnPullListeners(vararg listener: OnPullListener) = pullListeners.addAll(listOf(*listener))
    fun addOnReleaseListeners(vararg listener: OnPullListener) = releaseListeners.addAll(listOf(*listener))
    fun removePullListeners(vararg listener: OnPullListener) = pullListeners.removeAll(setOf(*listener))
    fun removeReleaseListeners(vararg listener: OnPullListener) = releaseListeners.removeAll(setOf(*listener))
    fun lock(lock: Lock) = locks.add(lock)
    fun unlock(lock: Lock) = locks.remove(lock)

    private fun onAction(dx: Float, dy: Float, actionId: Int) = when (actionId) {
        ACTION_PULL -> pullListeners.forEach { listener -> listener.onPull(Event(dx, dy)) }
        ACTION_RELEASE -> releaseListeners.forEach { listener -> listener.onPull(Event(dx, dy)) }
        else -> throw IllegalArgumentException("unknown action id")
    }

    /**
     * @param x origin view position before pull
     * @param y origin view position before pull
     * @param dx touch current position relative to origin touch position
     * @param dy touch current position relative to origin touch position
     * @param view target view
     */
    inner class Event internal constructor(
        val dx: Float,
        val dy: Float,

        val x: Float = originPosition.x,
        val y: Float = originPosition.y,
        val rangeHorizontal: Float = this@PullDispatcher.rangeHorizontal,
        val rangeVertical: Float = this@PullDispatcher.rangeVertical,
        val view: View = this@PullDispatcher.view
    ): Lock {
        fun lock(unique: Lock = this) = this@PullDispatcher.lock(unique)
        fun unlock(unique: Lock = this) = this@PullDispatcher.unlock(unique)
    }

    private inner class TouchHandler: View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (view !== this@PullDispatcher.eventSource) // never throws unless using reflection private access
                throw RuntimeException("This class must not be used to handle touch events of several views")
            if (!enabled)
                return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    originPosition = PointF(view.x, view.y)
                    touchOrigin = PointF(event.x, event.y)
                }
                MotionEvent.ACTION_UP ->
                    onAction(event.x - touchOrigin.x, event.y - touchOrigin.y, ACTION_RELEASE)
                MotionEvent.ACTION_MOVE ->
                    onAction(event.x - touchOrigin.x, event.y - touchOrigin.y, ACTION_PULL)
            }
            return true
        }
    }
}