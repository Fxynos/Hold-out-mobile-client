package com.vl.holdout.pull

class ChoiceHandler(var choiceListener: OnChoiceListener?): OnPullListener {
    companion object {
        const val CHOICE_LEFT = 1
        const val CHOICE_RIGHT = 2
    }

    override fun onPull(event: PullDispatcher.Event) =
        choiceListener?.onChoice(if (event.dx < 0) CHOICE_LEFT else CHOICE_RIGHT) ?: Unit

    fun interface OnChoiceListener {
        fun onChoice(choiceId: Int)
    }
}