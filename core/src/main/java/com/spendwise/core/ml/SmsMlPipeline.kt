import android.util.Log
import com.spendwise.core.ml.CategoryClassifierMl
import com.spendwise.core.ml.ClassifiedTxn
import com.spendwise.core.ml.IntentClassifierMl
import com.spendwise.core.ml.IntentType
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SenderClassifierMl

object SmsMlPipeline {

    suspend fun classify(
        raw: RawSms,
        parsedAmount: Double?,
        overrideProvider: suspend (String) -> String?
    ): ClassifiedTxn? {

        val amount = parsedAmount ?: return null

        val senderReason = StringBuilder()
        val intentReason = StringBuilder()
        val merchantReason = StringBuilder()
        val categoryReason = StringBuilder()

        // 🔁 0. Overrides to ignore completely
        //    (saved when user taps "Not expense")
        val bodyHashKey = "ignore:${raw.body.hashCode()}"
        if (overrideProvider(bodyHashKey) == "true") {
            intentReason.append("Ignored by user override for this SMS pattern.")
            Log.w("expense", raw.body)
            return null
        }

        // 1. Sender
        val senderType = SenderClassifierMl.classify(raw.sender, raw.body).also {
            senderReason.append("Detected sender type as $it because sender=${raw.sender}")
        }

        // 2. Intent
        val intentType = IntentClassifierMl.classify(senderType, raw.body).also {
            intentReason.append("Detected intent as $it based on keywords in message.")
        }

        if (intentType == IntentType.IGNORE) {
            intentReason.append(" → Not a real transaction (alert/due/auto-debit info).")
            Log.w("expense","intentReason $intentReason for ${raw.body}")
            return null
        }

        if (intentType !in listOf(IntentType.DEBIT, IntentType.CREDIT, IntentType.REFUND)) {
            intentReason.append(" → Not a financial transaction.")
            Log.w("expense","intentReason $intentReason for ${raw.body}")
            return null
        }

        // 3. Merchant
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

        // 4. Category
        val category = CategoryClassifierMl.classify(
            merchant = merchant,
            body = raw.body,
            intentType = intentType,
            overrideProvider = overrideProvider
        ).also { cat ->
            categoryReason.append("Category resolved as ${cat.name}.")
        }

        val isCredit = intentType == IntentType.CREDIT || intentType == IntentType.REFUND

        val reasons = MlReasonBundle(
            senderReason = senderReason.toString(),
            intentReason = intentReason.toString(),
            merchantReason = merchantReason.toString(),
            categoryReason = categoryReason.toString()
        )

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
