package com.spendwise.core.com.spendwise.core

import android.util.Log

enum class NetZeroReason {
    USER_SELF,
    TEMPLATE_MATCH,
    INTERNAL_TRANSFER_DETECTOR,
    WALLET_TOPUP,
    SYSTEM_ROUTING,
    REPROCESS_FALLBACK
}
object NetZeroDebugLogger {

    fun log(
        txId: Long,
        reason: NetZeroReason,
        extra: String? = null
    ) {
        Log.w(
            "NET_ZERO_DEBUG",
            buildString {
                append("txId=").append(txId)
                append(" isNetZero=true")
                append(" reason=").append(reason.name)
                if (extra != null) {
                    append(" extra=").append(extra)
                }
            }
        )
    }
}

