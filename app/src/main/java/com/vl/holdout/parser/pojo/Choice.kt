package com.vl.holdout.parser.pojo

class Choice internal constructor(
    override val id: String,
    val title: String
): Base(id) {
    lateinit var cards: Array<Card>
    lateinit var affects: Map<Bar, Affect>

    data class Affect(val type: Type, val value: Double) {
        enum class Type {
            MASK,
            EXPLICIT
        }
    }
}