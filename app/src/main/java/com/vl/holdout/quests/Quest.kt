package com.vl.holdout.quests

abstract class Quest(open val name: String) { // names are unique, there is no surrogate id
    /*override fun equals(other: Any?) = other?.let{
        it::class.java == this::class.java && (it as Quest).name == name
    } ?: false*/

    override fun equals(other: Any?) = other is Quest && other.name == name
}