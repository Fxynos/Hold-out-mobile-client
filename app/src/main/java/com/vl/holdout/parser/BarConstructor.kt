package com.vl.holdout.parser

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Choice
import java.io.File
import java.util.stream.Collectors

internal class BarConstructor(
    private val filesSource: File,
    private val choices: Repository<Choice>
): Constructor<Bar>("bar") {
    companion object {
        private const val VECTOR_SIZE = 480
    }

    override fun create(properties: Map<String, String>) = Bar(
        properties.require(type),
        properties.require("value").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"value\" property is decimal number"),
        loadDrawable(File(filesSource, properties.require("image")))
    )

    override fun finish(instance: Bar, properties: Map<String, String>) {
        instance.triggers = properties.require("triggers").split(DELIMITER_ARRAY).stream()
            .map { it.trim() }.filter(String::isNotBlank).map {
                it.split(DELIMITER_PAIR, limit = 2).takeIf { pair -> pair.size == 2 }
                    ?.let { pair -> pair[0].trim() to pair[1].trim() }
                    ?: throw ParseException("$type \"${properties.require(type)}\" triggers parse error: ${properties.require("triggers")}")
            }.collect(Collectors.toMap(
                { pair -> when (pair.first) {
                    "min" -> Bar.TRIGGER_MIN
                    "max" -> Bar.TRIGGER_MAX
                    else -> throw ParseException("Unknown trigger type in $type \"${
                        properties.require(type)
                    }\": ${pair.first}")
                } },
                { pair -> choices[pair.second] }
            ))
    }

    private fun loadDrawable(resource: File): Drawable =
        if (resource.extension == "svg")
            BitmapDrawable(Bitmap.createBitmap(VECTOR_SIZE, VECTOR_SIZE, Bitmap.Config.ARGB_8888).also {
                val svg = SVG.getFromInputStream(resource.inputStream())
                svg.documentWidth = VECTOR_SIZE.toFloat()
                svg.documentHeight = VECTOR_SIZE.toFloat()
                svg.renderToCanvas(Canvas(it))
            }) else BitmapDrawable.createFromPath(resource.absolutePath)
                ?: throw ParseException("Couldn't load file: ${resource.absolutePath}")
}