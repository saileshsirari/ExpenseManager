package com.spendwise.core.extensions

import java.time.YearMonth

// ------------------------
// QUARTER HELPERS
// ------------------------
fun YearMonth.quarter(): Int = ((this.monthValue - 1) / 3) + 1

fun YearMonth.startOfQuarter(): YearMonth {
    val q = this.quarter()
    val startMonth = (q - 1) * 3 + 1
    return YearMonth.of(this.year, startMonth)
}

fun YearMonth.endOfQuarter(): YearMonth {
    val q = this.quarter()
    val endMonth = q * 3
    return YearMonth.of(this.year, endMonth)
}

// Next / Previous quarter
fun YearMonth.nextQuarter(): YearMonth {
    val q = this.quarter()
    return if (q == 4)
        YearMonth.of(this.year + 1, 1)
    else
        YearMonth.of(this.year, q * 3 + 1)
}

fun YearMonth.previousQuarter(): YearMonth {
    val q = this.quarter()
    return if (q == 1)
        YearMonth.of(this.year - 1, 10)
    else
        YearMonth.of(this.year, (q - 2) * 3 + 1)
}

// ------------------------
// YEAR HELPERS
// ------------------------
fun YearMonth.nextYear(): YearMonth = YearMonth.of(this.year + 1, 1)
fun YearMonth.previousYear(): YearMonth = YearMonth.of(this.year - 1, 1)
