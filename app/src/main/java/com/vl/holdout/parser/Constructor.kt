package com.vl.holdout.parser

import com.vl.holdout.parser.pojo.Base

internal abstract class Constructor<T: Base>(protected val type: String) {
    companion object {
        @JvmStatic
        protected val DELIMITER_ARRAY = "\\+"
        @JvmStatic
        protected val DELIMITER_LINEFEED = "\\n"
        @JvmStatic
        protected val DELIMITER_PAIR = "="
        @JvmStatic
        protected val CARDS_ALL = "ALL"
    }

    /**
     * @param properties must also contain a pair of type and name
     */
    abstract fun create(properties: Map<String, String>): T
    abstract fun finish(instance: T, properties: Map<String, String>)

    protected fun (Map<String, String>).require(property: String) = this[property] ?:
        throw ParseException("\"$type\" must contain \"$property\" property")
}