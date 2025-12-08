package com.spendwise.core.ml.debug

import android.util.Log
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.IntentType
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SenderType

object MlDebugLogger {

    var enabled = true   // allow disabling in production

    fun logPipelineStep(
        raw: RawSms,
        senderType: SenderType,
        intentType: IntentType,
        merchant: String?,
        category: CategoryType,
        amount: Double,
        reason: MlReasonBundle
    ) {
        if (!enabled) return

        Log.d("ML_PIPELINE", """
            ---- ML CLASSIFICATION ----
            SMS From: ${raw.sender}
            Body: ${raw.body.take(200)}...
            Amount: $amount

            SenderType  => $senderType
            IntentType  => $intentType
            Merchant    => $merchant
            Category    => $category

            ------- REASONS --------
            SenderReason:   ${reason.senderReason}
            IntentReason:   ${reason.intentReason}
            MerchantReason: ${reason.merchantReason}
            CategoryReason: ${reason.categoryReason}
            -------------------------
        """.trimIndent())
    }
}
