package com.spendwise.core.extensions

import java.time.YearMonth

fun YearMonth.toQuarterName(): String {
    val q = ((this.monthValue - 1) / 3) + 1
    return "Q$q ${this.year}"
}