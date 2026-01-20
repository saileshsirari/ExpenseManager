package com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper

import com.spendwise.core.model.TransactionCoreModel
import com.spendwise.feature.smsimport.data.SmsEntity

fun SmsEntity.toDomain(): TransactionCoreModel =
    TransactionCoreModel(
        id = id,
        sender = sender,
        body = body,
        timestamp = timestamp,
        amount = amount,
        merchant = merchant,
        type = type,
        category = category,
        isIgnored = isIgnored,
        linkId = linkId,
        linkType = linkType,
        linkConfidence = linkConfidence,
        isNetZero = isNetZero
    )

