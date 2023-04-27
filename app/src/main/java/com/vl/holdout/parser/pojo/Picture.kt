package com.vl.holdout.parser.pojo

import android.graphics.Canvas
import java.util.function.Consumer

class Picture internal constructor(
    override val id: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
): Base(id) {
    lateinit var layers: Array<Consumer<Canvas>>
}