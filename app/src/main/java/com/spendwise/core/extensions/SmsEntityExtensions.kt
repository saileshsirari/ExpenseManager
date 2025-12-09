package com.spendwise.core.extensions

import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.Instant
import java.time.ZoneId

fun SmsEntity.localDate(): java.time.LocalDate =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
