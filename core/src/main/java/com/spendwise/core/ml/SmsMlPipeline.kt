package com.spendwise.core.ml

/**
 * Given raw SMS + parsed amount (from your existing regex parser),
 * decide if it's a real transaction and classify it.
 *
 * If returns null → ignore this SMS (not a txn).
 */
object SmsMlPipeline {

   suspend fun classify(raw: RawSms, parsedAmount: Double?, overrideProvider: suspend (String) -> String?): ClassifiedTxn? {
        val amount = parsedAmount ?: return null

        val senderReason = StringBuilder()
        val intentReason = StringBuilder()
        val merchantReason = StringBuilder()
        val categoryReason = StringBuilder()

        val senderType = SenderClassifierMl.classify(raw.sender, raw.body).also {
            senderReason.append("Detected sender type as $it because sender=${raw.sender}")
        }

        val intentType = IntentClassifierMl.classify(senderType, raw.body).also {
            intentReason.append("Detected intent as $it based on keywords in message.")
        }

        if (intentType !in listOf(IntentType.DEBIT, IntentType.CREDIT, IntentType.REFUND)) {
            intentReason.append(" → Not a financial transaction.")
            return null
        }

        val merchant = MerchantExtractorMl.extract(
            senderType,
            raw.sender,
            raw.body,
            overrideProvider
        )

        val category = CategoryClassifierMl.classify(
            merchant,
            raw.body,
            intentType,
            overrideProvider
        )


        val isCredit = intentType == IntentType.CREDIT || intentType == IntentType.REFUND

        val reasons = MlReasonBundle(
            senderReason.toString(),
            intentReason.toString(),
            merchantReason.toString(),
            categoryReason.toString()
        )

        // Debug log
    /*    MlDebugLogger.logPipelineStep(
            raw = raw,
            senderType = senderType,
            intentType = intentType,
            merchant = merchant,
            category = category,
            amount = amount,
            reason = reasons
        )*/

        return ClassifiedTxn(
            rawSms = raw,
            senderType = senderType,
            intentType = intentType,
            merchant = merchant,
            category = category,
            amount = amount,
            isCredit = isCredit,
            explanation = reasons   // ← Add this field
        )
    }
}
