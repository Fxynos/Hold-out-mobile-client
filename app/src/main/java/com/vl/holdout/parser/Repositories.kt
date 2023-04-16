package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.*

data class Repositories(
    val core: Repository<Core>,
    val bar: Repository<Bar>,
    val picture: Repository<Picture>,
    val choice: Repository<Choice>,
    val card: Repository<Card>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun fromMap(map: Map<String, Repository<*>>) = Repositories (
            map["core"] as Repository<Core>,
            map["bar"] as Repository<Bar>,
            map["picture"] as Repository<Picture>,
            map["choice"] as Repository<Choice>,
            map["card"] as Repository<Card>
        )
    }
}