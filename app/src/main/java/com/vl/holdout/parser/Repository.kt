package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Base

class Repository<T: Base> internal constructor() {
    private val map: MutableMap<String, T> = HashMap()

    internal val values: Collection<T>
        get() = map.values

    operator fun get(id: String) = map[id] ?: throw ParseException("object \"$id\" not found")

    internal operator fun plusAssign(instance: T) {
        map[instance.id] = instance
    }
}