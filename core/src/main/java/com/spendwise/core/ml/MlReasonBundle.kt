package com.spendwise.core.ml

data class MlReasonBundle(
    val senderReason: String,
    val intentReason: String,
    val merchantReason: String,
    val categoryReason: String
)
