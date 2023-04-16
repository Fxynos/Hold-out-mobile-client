package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Card
import com.vl.holdout.parser.pojo.Choice
import java.util.stream.Collectors
import kotlin.streams.toList

internal class ChoiceConstructor(
    private val cardsRepository: Repository<Card>,
    private val barsRepository: Repository<Bar>
): Constructor<Choice>("choice") {
    private val allCards by lazy { cardsRepository.values.toTypedArray() }

    override fun create(properties: Map<String, String>) = Choice(
        properties.require(type),
        properties.require("title").replace(DELIMITER_LINEFEED, "\n")
    )

    override fun finish(instance: Choice, properties: Map<String, String>) {
        instance.cards = (
                properties.require("cards").let { cards ->
                    if (cards == CARDS_ALL)
                        allCards
                    else
                        cards.split(DELIMITER_ARRAY).stream()
                            .map { cardsRepository[it.trim()] }.toList().toTypedArray()
                }
        ).also {
                if (it.isEmpty())
                    throw ParseException("$type \"${instance.id}\" must contain 1 or more cards")
        }
        instance.affects = properties.require("affects").let { property ->
            if (property.isBlank())
                mapOf()
            else
                property.split(DELIMITER_ARRAY).stream()
                    .map { sPair ->
                        sPair.split(DELIMITER_PAIR, limit = 2).let { it ->
                            it[0].trim() to if (it.size > 1) it[1].trim() else throw ParseException(sPair)
                        }
                    }
                    .map { (barId, sAffect) ->
                        var affectType = Choice.Affect.Type.MASK
                        barsRepository[barId] to (
                                if (sAffect.startsWith("\$"))
                                    sAffect.substring(1).also { affectType = Choice.Affect.Type.EXPLICIT }
                                else
                                    sAffect
                                ).let {
                                Choice.Affect(
                                    affectType,
                                    (
                                        it.toDoubleOrNull() ?:
                                        throw ParseException("\"${instance.id}\" choice affect for bar \"$barId\" is not decimal number")
                                    ).also { value ->
                                            if (value > 1 || (value < 0 && affectType == Choice.Affect.Type.EXPLICIT) || value < -1)
                                                throw ParseException(
                                                    "\"${instance.id}\" choice affect must be in bounds of [0.0; 1.0] for \"${barId}\""
                                                )
                                    }
                                )
                            }
                    }.collect(Collectors.toMap(
                        { (bar, _) -> bar },
                        { (_, affect) -> affect }
                    ))
        }
    }
}