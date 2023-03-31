package com.vl.holdout.pull

fun interface OnPullListener {
    fun onPull(event: PullDispatcher.Event)
}