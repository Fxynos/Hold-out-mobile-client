package com.vl.holdout.parser.pojo

import android.graphics.drawable.Drawable

class Bar internal constructor(
    override val id: String,
    val value: Double, // initial value
    val image: Drawable
): Base(id) {
    companion object {
        const val TRIGGER_MIN = 1
        const val TRIGGER_MAX = 2
    }

    lateinit var triggers: Map<Int, Choice>
}