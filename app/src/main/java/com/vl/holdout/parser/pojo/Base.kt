package com.vl.holdout.parser.pojo

abstract class Base internal constructor(open val id: String/*, name: String? = null*/) {
    //internal val typeName = name ?: getName(this) TODO

    //private inline fun <reified T> getName(instance: T) = this::class.java.simpleName.lowercase()
}