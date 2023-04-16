package com.vl.holdout.parser

class ParseException(val area: String): Exception("Parser exception: $area")