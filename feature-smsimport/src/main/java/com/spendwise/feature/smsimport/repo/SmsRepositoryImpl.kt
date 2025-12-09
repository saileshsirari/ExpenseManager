package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.IgnorePatternBuilder
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.domain.SmsRepository
import com.spendwise.feature.smsimport.SmsParser
import com.spendwise.feature.smsimport.SmsReaderImpl
import com.spendwise.feature.smsimport.data.AppDatabase
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.data.UserMlOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class SmsRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : SmsRepository {


    // ------------------------------------------------------------
    // IMPORT ALL
    // ------------------------------------------------------------
    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<List<SmsEntity>> =
        flow {
            val resolver = resolverProvider()
            val lastTimestamp = db.smsDao().getLastTimestamp() ?: 0L

            val rawList = withContext(Dispatchers.IO) {
                SmsReaderImpl(resolver).readSince(lastTimestamp)
            }

            val ignorePatterns = db.userMlOverrideDao().getIgnorePatterns()
                .map { Regex(it) }

            val classified = rawList.mapNotNull { sms ->

                val bodyLower = sms.body.lowercase()

                if (ignorePatterns.any { it.containsMatchIn(bodyLower) })
                    return@mapNotNull null

                val amount = SmsParser.parseAmount(sms.body)
                if (amount == null || amount <= 0) return@mapNotNull null

                val result = SmsMlPipeline.classify(
                    raw = RawSms(sms.sender, sms.body, sms.timestamp),
                    parsedAmount = amount,
                    overrideProvider = { key ->
                        val v = db.userMlOverrideDao().getValue(key)
                        Log.d("OVERRIDE_LOOKUP", "import key=$key => $v")
                        v
                    }
                ) ?: return@mapNotNull null

                SmsEntity(
                    sender = result.rawSms.sender,
                    body = result.rawSms.body,
                    timestamp = result.rawSms.timestamp,
                    amount = result.amount,
                    merchant = result.merchant,
                    type = if (result.isCredit) "CREDIT" else "DEBIT",
                    category = result.category.name
                )
            }

            if (classified.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    classified.chunked(300).forEach { db.smsDao().insertAll(it) }
                }
            }

            emitAll(db.smsDao().getAll())
        }


    // ------------------------------------------------------------
    // MANUAL SAVE
    // ------------------------------------------------------------
    override suspend fun saveManual(sender: String, body: String, timestamp: Long) {
        val amount = SmsParser.parseAmount(body) ?: return
        db.smsDao().insert(
            SmsEntity(
                sender = sender,
                body = body,
                timestamp = timestamp,
                amount = amount,
                merchant = null,
                type = "MANUAL",
                category = "OTHER"
            )
        )
    }


    // ------------------------------------------------------------
    // MERCHANT OVERRIDE  (CORRECTED)
    // ------------------------------------------------------------
    suspend fun saveMerchantOverride(originalMerchant: String, newMerchant: String) {

        // normalize only merchant name, not full key
        val norm = MerchantExtractorMl.normalize(originalMerchant)

        val key = "merchant:$norm"

        Log.d("OVERRIDE_SAVE", "Saving override: $key -> $newMerchant")

        db.userMlOverrideDao().save(
            UserMlOverride(key, newMerchant.trim())
        )
    }


    // ------------------------------------------------------------
    // IGNORE PATTERN
    // ------------------------------------------------------------
    suspend fun saveIgnorePattern(body: String) {
        val pattern = IgnorePatternBuilder.build(body)
        db.userMlOverrideDao().save(
            UserMlOverride("ignore_pattern:${pattern.hashCode()}", pattern)
        )
    }


    // ------------------------------------------------------------
    // CATEGORY OVERRIDE  (CORRECTED)
    // ------------------------------------------------------------
    suspend fun saveCategoryOverride(merchant: String, newCategory: String) {
        val normalized = MerchantExtractorMl.normalize(merchant)
        db.userMlOverrideDao().save(
            UserMlOverride("category:$normalized", newCategory)
        )
    }


    // ------------------------------------------------------------
    // ML EXPLANATION
    // ------------------------------------------------------------
    suspend fun getMlExplanationFor(tx: SmsEntity): MlReasonBundle? {
        val amount = tx.amount
        if (amount <= 0) return null

        return SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = amount,
            overrideProvider = { key ->
                val v = db.userMlOverrideDao().getValue(key)
                Log.d("OVERRIDE_LOOKUP", "exp key=$key => $v")
                v
            }
        )?.explanation
    }


    // ------------------------------------------------------------
    // RECLASSIFY SINGLE  (FULLY FIXED)
    // ------------------------------------------------------------
    override suspend fun reclassifySingle(id: Long): SmsEntity? {
        val tx = db.smsDao().getById(id) ?: return null

        val parsedAmount = SmsParser.parseAmount(tx.body) ?: return null

        val normMerchant = MerchantExtractorMl.normalize(tx.merchant ?: tx.sender ?: "")
        val normSender = MerchantExtractorMl.normalize(tx.sender ?: "")

        Log.d("RECLASSIFY", "Normalized merchant=$normMerchant, sender=$normSender")

        val result = SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = parsedAmount,
            overrideProvider = { key ->

                // 1) Exact key
                db.userMlOverrideDao().getValue(key)?.also {
                    Log.d("OVERRIDE_LOOKUP", "match key=$key => $it")
                    return@classify it
                }

                // 2) merchant:<normalizedMerchant>
                db.userMlOverrideDao().getValue("merchant:$normMerchant")?.also {
                    Log.d("OVERRIDE_LOOKUP", "match merchant:$normMerchant => $it")
                    return@classify it
                }

                // 3) merchant:<normalizedSender>
                db.userMlOverrideDao().getValue("merchant:$normSender")?.also {
                    Log.d("OVERRIDE_LOOKUP", "match merchant:$normSender => $it")
                    return@classify it
                }

                Log.d("OVERRIDE_LOOKUP", "NO MATCH for key=$key")
                null
            }
        ) ?: return null

        val updated = tx.copy(
            merchant = result.merchant,
            category = result.category.name,
            type = if (result.isCredit) "CREDIT" else "DEBIT"
        )

        Log.d("RECLASSIFY", "Updated merchant=${updated.merchant}")

        db.smsDao().update(updated)
        return updated
    }


    // ------------------------------------------------------------
    // GET ALL
    // ------------------------------------------------------------
    override fun getAll(): Flow<List<SmsEntity>> {
        return db.smsDao().getAll()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveManualExpense(
        amount: Double,
        merchant: String,
        category: CategoryType,
        date: LocalDate,
        note: String
    ) {
        db.smsDao().insert(
            SmsEntity(
                sender = "MANUAL",
                body = note,
                timestamp = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000,
                amount = amount,
                merchant = merchant,
                type = "DEBIT",
                category = category.name
            )
        )
    }
   override suspend fun markIgnored(tx: SmsEntity) {
        val updated = tx.copy(isIgnored = true)
        db.smsDao().update(updated)
    }
   override suspend fun setIgnored(id: Long, ignored: Boolean) {
        db.smsDao().setIgnored(id, if (ignored) 1 else 0)
    }
}
