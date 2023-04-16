package com.vl.holdout.parser.pojo

import android.graphics.drawable.Drawable

class Bar internal constructor(
    override val id: String,
    val value: Double,
    val image: Drawable? // TODO not-null
): Base(id)