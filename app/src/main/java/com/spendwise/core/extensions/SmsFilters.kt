package com.spendwise.core.extensions

import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// ------------------- ACTIVE FILTER -------------------
fun List<SmsEntity>.active() =
    this.filter { !it.isIgnored }

// ------------------- MONTH FILTER -------------------
fun List<SmsEntity>.inMonth(month: YearMonth) =
    this.filter { YearMonth.from(it.localDate()) == month }

// ------------------- QUARTER FILTER -------------------
fun List<SmsEntity>.inQuarter(month: YearMonth): List<SmsEntity> {
    val q = ((month.monthValue - 1) / 3) + 1  // Q1…Q4
    val startMonth = (q - 1) * 3 + 1
    val endMonth = startMonth + 2

    return this.filter { tx ->
        val date = tx.localDate()
        date.year == month.year &&
                date.monthValue in startMonth..endMonth
    }
}

// ------------------- YEAR FILTER -------------------
fun List<SmsEntity>.inYear(year: Int) =
    this.filter { it.localDate().year == year }

// ------------------- DAY FILTER -------------------
fun List<SmsEntity>.onDay(day: Int, month: YearMonth) =
    if (day < 1) emptyList()
    else this.filter { tx ->
        val date = tx.localDate()
        date.dayOfMonth == day &&
                YearMonth.from(date) == month
    }

// ------------------- TYPE FILTER -------------------
fun List<SmsEntity>.ofType(type: String?) =
    if (type == null) this
    else this.filter { it.type.equals(type, true) }


