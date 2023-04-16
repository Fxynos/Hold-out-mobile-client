package com.vl.holdout.parser

import android.graphics.Canvas
import com.vl.holdout.parser.pojo.Picture
import kotlin.streams.toList

internal class PictureConstructor(private val pictureRepository: Repository<Picture>): Constructor<Picture>("picture") {

    override fun create(properties: Map<String, String>) = Picture(
        properties.require(type),
        properties.require("x").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"x\" property is decimal number"),
        properties.require("y").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"y\" property is decimal number"),
        properties.require("width").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"width\" property is decimal number"),
        properties.require("height").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"height\" property is decimal number")
    )

    override fun finish(instance: Picture, properties: Map<String, String>) {
        instance.layers = properties.require("layers").split(DELIMITER_ARRAY).stream()
            .map { sPair -> sPair.split(DELIMITER_PAIR, limit = 2).let { it[0].trim().lowercase() to if (it.size > 1) it[1].trim() else throw ParseException("$type \"${instance.id}\": $sPair") } }
            .map {
                when (it.first) { // TODO drawing
                    "color" -> { _: Canvas -> }
                    "file" -> { _: Canvas -> }
                    "picture" -> { canvas: Canvas -> pictureRepository[it.second].layers.forEach { it(canvas) } } // TODO looping check
                    else -> throw ParseException("$type \"${instance.id}\" unknown layer type: ${properties.require("layers")}")
                }
            }.toList().toTypedArray()
    }
}