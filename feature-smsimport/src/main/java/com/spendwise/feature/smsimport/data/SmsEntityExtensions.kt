package com.spendwise.domain.com.spendwise.feature.smsimport.data


import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Convert timestamp to LocalDate
fun SmsEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

// Only non-ignored
fun List<SmsEntity>.active(): List<SmsEntity> =
    this.filter { !it.isIgnored }

// In a specific month
fun List<SmsEntity>.inMonth(month: YearMonth): List<SmsEntity> =
    this.filter { YearMonth.from(it.localDate()) == month }

// In a quarter (based on anchor YearMonth)
fun List<SmsEntity>.inQuarter(anchor: YearMonth): List<SmsEntity> {
    val q = ((anchor.monthValue - 1) / 3) + 1
    val startMonth = (q - 1) * 3 + 1
    val endMonth = startMonth + 2
    return this.filter { tx ->
        val d = tx.localDate()
        d.year == anchor.year && d.monthValue in startMonth..endMonth
    }
}

// In a year
fun List<SmsEntity>.inYear(year: Int): List<SmsEntity> =
    this.filter { it.localDate().year == year }

// Filter by type (DEBIT/CREDIT)
fun List<SmsEntity>.ofType(type: String?): List<SmsEntity> =
    if (type == null) this else this.filter { it.type.equals(type, ignoreCase = true) }

