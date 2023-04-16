package com.vl.holdout.parser.pojo

import android.graphics.drawable.Drawable

class Core internal constructor(
    override val id: String,
    val name: String,
    val description: String,
    val image: Drawable? // TODO not-null
): Base(id) {
    lateinit var choice: Choice // start (title of this choice is invisible)
    lateinit var bars: Array<Bar>
}