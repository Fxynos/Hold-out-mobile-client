package com.vl.holdout.pull

class ConditionalPullHandler(
    private val onTrue: Collection<OnPullListener>,
    private val onFalse: Collection<OnPullListener>,
    private val condition: (PullDispatcher.Event) -> Boolean
): OnPullListener {
    override fun onPull(event: PullDispatcher.Event) =
        (if (condition(event)) onTrue else onFalse).forEach() { listener -> listener.onPull(event) }
}