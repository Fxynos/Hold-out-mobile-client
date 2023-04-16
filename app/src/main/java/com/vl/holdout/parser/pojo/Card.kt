package com.vl.holdout.parser.pojo

class Card internal constructor(
    override val id: String,
    val title: String,
    val text: String
): Base(id) {
    lateinit var choices: Array<Choice>
    lateinit var picture: Picture
}