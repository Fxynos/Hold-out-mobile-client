package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Card
import com.vl.holdout.parser.pojo.Choice
import com.vl.holdout.parser.pojo.Picture
import kotlin.streams.toList

internal class CardConstructor(
    private val choicesRepository: Repository<Choice>,
    private val picturesRepository: Repository<Picture>
): Constructor<Card>("card") {

    override fun create(properties: Map<String, String>) = Card(
        properties.require(type).also { if (it == CARDS_ALL) throw ParseException("card uses reserved name \"$it\"") },
        properties.require("title").replace(DELIMITER_LINEFEED, "\n"),
        properties.require("text").replace(DELIMITER_LINEFEED, "\n")
    )

    override fun finish(instance: Card, properties: Map<String, String>) {
        instance.choices = properties.require("choices").split(DELIMITER_ARRAY).stream()
            .map { choicesRepository[it.trim()] }.toList().toTypedArray().also {
                if (it.size != 2 && it.isNotEmpty())
                    throw ParseException("$type \"${instance.id}\" must contain 2 choices or no one if it is ending card")
            }
        instance.picture = picturesRepository[properties.require("picture")]
    }
}