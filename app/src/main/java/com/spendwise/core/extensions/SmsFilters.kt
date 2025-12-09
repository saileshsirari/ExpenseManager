package com.spendwise.core.extensions


import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// -------------------------------
// FILTER OUT IGNORED TRANSACTIONS
// -------------------------------
fun List<SmsEntity>.active(): List<SmsEntity> =
    this.filter { !it.isIgnored }

// -------------------------------
// FILTER BY MONTH
// -------------------------------
fun List<SmsEntity>.inMonth(month: YearMonth): List<SmsEntity> =
    this.filter { tx ->
        YearMonth.from(
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        ) == month
    }

// -------------------------------
// FILTER BY DAY
// -------------------------------
fun List<SmsEntity>.onDay(day: Int, month: YearMonth): List<SmsEntity> =
    this.filter { tx ->
        val date = Instant.ofEpochMilli(tx.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        date.dayOfMonth == day && YearMonth.from(date) == month
    }

// -------------------------------
// FILTER BY TYPE: DEBIT / CREDIT
// -------------------------------
fun List<SmsEntity>.ofType(type: String?): List<SmsEntity> =
    if (type == null) this
    else this.filter { it.type.equals(type, ignoreCase = true) }
