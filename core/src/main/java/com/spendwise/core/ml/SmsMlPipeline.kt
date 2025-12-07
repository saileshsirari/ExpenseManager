package com.spendwise.core.ml

object SmsMlPipeline {

    suspend fun classify(
        raw: RawSms,
        parsedAmount: Double?,
        overrideProvider: suspend (String) -> String?
    ): ClassifiedTxn? {

        val amount = parsedAmount ?: return null

        // Build explanation output
        val senderReason = StringBuilder()
        val intentReason = StringBuilder()
        val merchantReason = StringBuilder()
        val categoryReason = StringBuilder()

        // -----------------------------------------------------------
        // 1️⃣ Sender type classification
        // -----------------------------------------------------------
        val senderType = SenderClassifierMl.classify(raw.sender, raw.body).also {
            senderReason.append("Detected sender type as $it because sender=${raw.sender}")
        }

        // -----------------------------------------------------------
        // 2️⃣ Intent classification (debit/credit/refund/etc)
        // -----------------------------------------------------------
        val intentType = IntentClassifierMl.classify(senderType, raw.body).also {
            intentReason.append("Detected intent as $it based on keywords in message.")
        }

        if (intentType !in listOf(IntentType.DEBIT, IntentType.CREDIT, IntentType.REFUND)) {
            intentReason.append(" → Not a financial transaction.")
            return null
        }

        // -----------------------------------------------------------
        // 3️⃣ Merchant extraction
        // -----------------------------------------------------------
        val merchant = MerchantExtractorMl.extract(
            senderType = senderType,
            sender = raw.sender,
            body = raw.body,
            overrideProvider = overrideProvider
        ).also { m ->
            if (m != null)
                merchantReason.append("Merchant resolved as '$m'.")
            else
                merchantReason.append("No merchant detected.")
        }

        // -----------------------------------------------------------
        // 4️⃣ Category classification
        // -----------------------------------------------------------
        val category = CategoryClassifierMl.classify(
            merchant = merchant,
            body = raw.body,
            intentType = intentType,
            overrideProvider = overrideProvider
        ).also { cat ->
            if (cat != null)
                categoryReason.append("Category resolved as ${cat.name}.")
            else
                categoryReason.append("Category not identifiable.")
        }

        // -----------------------------------------------------------
        // 5️⃣ Credit/Debit logic
        // -----------------------------------------------------------
        val isCredit = intentType == IntentType.CREDIT || intentType == IntentType.REFUND

        // -----------------------------------------------------------
        // 6️⃣ Build explanation bundle
        // -----------------------------------------------------------
        val reasons = MlReasonBundle(
            senderReason = senderReason.toString(),
            intentReason = intentReason.toString(),
            merchantReason = merchantReason.toString(),
            categoryReason = categoryReason.toString()
        )

        // -----------------------------------------------------------
        // 7️⃣ Debug log (optional)
        // -----------------------------------------------------------
        /*
        MlDebugLogger.logPipelineStep(
            raw = raw,
            senderType = senderType,
            intentType = intentType,
            merchant = merchant,
            category = category,
            amount = amount,
            reason = reasons
        )
        */

        // -----------------------------------------------------------
        // 8️⃣ Return final classified transaction
        // -----------------------------------------------------------
        return ClassifiedTxn(
            rawSms = raw,
            senderType = senderType,
            intentType = intentType,
            merchant = merchant,
            category = category,
            amount = amount,
            isCredit = isCredit,
            explanation = reasons
        )
    }
}
