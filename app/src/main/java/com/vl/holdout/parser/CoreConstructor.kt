package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Choice
import com.vl.holdout.parser.pojo.Core
import java.io.File
import kotlin.streams.toList

internal class CoreConstructor(
    private val choicesRepository: Repository<Choice>,
    private val barsRepository: Repository<Bar>
): Constructor<Core>("core") {

    override fun create(properties: Map<String, String>) = Core(
        properties.require(type),
        properties.require("name"),
        properties.require("description"),
        null // TODO "image"
    )

    override fun finish(instance: Core, properties: Map<String, String>) {
        instance.choice = choicesRepository[properties.require("start")]
        instance.bars = properties.require("bars").split(DELIMITER_ARRAY).stream()
            .map { barsRepository[it.trim()] }.toList().toTypedArray()
    }
}