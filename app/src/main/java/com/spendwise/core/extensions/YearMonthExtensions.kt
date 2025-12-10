package com.spendwise.core.extensions

import java.time.YearMonth

fun YearMonth.quarter(): Int = ((this.monthValue - 1) / 3) + 1

fun YearMonth.toQuarterTitle(): String = "Q${this.quarter()} ${this.year}"

fun YearMonth.nextQuarter(): YearMonth {
    val q = this.quarter()
    return if (q == 4) YearMonth.of(this.year + 1, 1)
    else YearMonth.of(this.year, q * 3 + 1)
}

fun YearMonth.previousQuarter(): YearMonth {
    val q = this.quarter()
    return if (q == 1) YearMonth.of(this.year - 1, 10)
    else YearMonth.of(this.year, (q - 2) * 3 + 1)
}

fun YearMonth.nextYear(): YearMonth = YearMonth.of(this.year + 1, 1)
fun YearMonth.previousYear(): YearMonth = YearMonth.of(this.year - 1, 1)
