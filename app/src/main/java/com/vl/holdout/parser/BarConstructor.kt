package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Bar

internal class BarConstructor: Constructor<Bar>("bar") {

    override fun create(properties: Map<String, String>) = Bar(
        properties.require(type),
        properties.require("value").toDoubleOrNull() ?: throw ParseException("$type \"${properties.require(type)}\" \"value\" property is decimal number"),
        null // TODO "image"
    )

    override fun finish(instance: Bar, properties: Map<String, String>) = Unit
}