
import com.spendwise.core.ml.CategoryClassifierMl
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.ClassifiedTxn
import com.spendwise.core.ml.IntentClassifierMl
import com.spendwise.core.ml.IntentType
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SenderClassifierMl
import com.spendwise.core.transfer.InternalTransferDetector
import com.spendwise.core.transfer.SelfRecipientProvider
import com.spendwise.core.transfer.TransferBuffer

object SmsMlPipeline {

    suspend fun classify(
        raw: RawSms,
        parsedAmount: Double?,
        overrideProvider: suspend (String) -> String?,
        selfRecipientProvider: SelfRecipientProvider
    ): ClassifiedTxn? {

        val amount = parsedAmount ?: return null

        // 0. User ignore override
        if (overrideProvider("ignore:${raw.body.hashCode()}") == "true") {
            return null
        }

        // 1. Sender
        val senderType = SenderClassifierMl.classify(raw.sender, raw.body)

        // 2. Intent
        val intentType = IntentClassifierMl.classify(senderType, raw.body)
        if (intentType == IntentType.IGNORE) return null
        if (intentType !in listOf(IntentType.DEBIT, IntentType.CREDIT, IntentType.REFUND)) return null

        // 🔥 2.5 INTERNAL TRANSFER DETECTION
        val transferInfo = InternalTransferDetector.detect(
            senderType = senderType,
            body = raw.body,
            amount = amount,
            selfRecipientProvider = selfRecipientProvider
        )

        val isInternalTransfer = when {
            transferInfo == null -> false

            // 🔒 SINGLE SMS: debit + credit together
            transferInfo.hasDebitAccount && transferInfo.hasCreditAccount
                -> true

            // 🔒 SPLIT SMS: wait for pair
            else -> TransferBuffer.match(transferInfo)
        }


        // 3. Merchant (safe to run always)
        val merchant = MerchantExtractorMl.extract(
            senderType = senderType,
            sender = raw.sender,
            body = raw.body,
            overrideProvider = overrideProvider
        )

        // 4. Category
        val category =
            if (isInternalTransfer) {
                CategoryType.TRANSFER
            } else {
                CategoryClassifierMl.classify(
                    merchant = merchant,
                    body = raw.body,
                    intentType = intentType,
                    overrideProvider = overrideProvider
                )
            }

        val isCredit = intentType == IntentType.CREDIT || intentType == IntentType.REFUND

        return ClassifiedTxn(
            rawSms = raw,
            senderType = senderType,
            intentType = intentType,
            merchant = merchant,
            category = category,
            amount = amount,
            isCredit = isCredit,
            explanation = MlReasonBundle(
                senderReason = "Sender=$senderType",
                intentReason = "Intent=$intentType",
                merchantReason = "Merchant=$merchant",
                categoryReason = "Category=$category"
            )
        )
    }
}
