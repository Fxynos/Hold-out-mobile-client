package com.vl.holdout.parser

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import com.vl.holdout.parser.pojo.Bar
import java.io.File

internal class BarConstructor(
    private val filesSource: File
): Constructor<Bar>("bar") {
    companion object {
        private const val VECTOR_SIZE = 480
    }

    override fun create(properties: Map<String, String>) = Bar(
        properties.require(type),
        properties.require("value").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"value\" property is decimal number"),
        loadDrawable(File(filesSource, properties.require("image")))
    )

    override fun finish(instance: Bar, properties: Map<String, String>) = Unit

    private fun loadDrawable(resource: File): Drawable =
        if (resource.extension == "svg")
            BitmapDrawable(Bitmap.createBitmap(VECTOR_SIZE, VECTOR_SIZE, Bitmap.Config.ARGB_8888).also {
                val svg = SVG.getFromInputStream(resource.inputStream())
                svg.documentWidth = VECTOR_SIZE.toFloat()
                svg.documentHeight = VECTOR_SIZE.toFloat()
                svg.renderToCanvas(Canvas(it))
            }) else BitmapDrawable.createFromPath(resource.absolutePath)!!
}