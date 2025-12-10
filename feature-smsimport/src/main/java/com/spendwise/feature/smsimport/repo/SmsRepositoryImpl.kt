package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.spendwise.core.linked.LinkedTransactionDetector
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.IgnorePatternBuilder
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.domain.SmsRepository
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
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
    private val db: AppDatabase,
    private val linkedDetector: LinkedTransactionDetector
) : SmsRepository {


    // ------------------------------------------------------------
    // IMPORT ALL
    // ------------------------------------------------------------
    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<List<SmsEntity>> =
        flow {

            val resolver = resolverProvider()

            // -----------------------------
            // READ ONLY CURRENT MONTH
            // -----------------------------
            val now = LocalDate.now()
            val monthStart = now.minusMonths(1).withDayOfMonth(1)
            val monthStartMillis = monthStart
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond() * 1000

            val lastTimestamp = monthStartMillis - 3600_000L

            Log.w("expense", "Importing SMS since: $monthStart  ($lastTimestamp)")


            // -----------------------------
            // READ SMS SINCE TIMESTAMP
            // -----------------------------
            val rawList = withContext(Dispatchers.IO) {
                SmsReaderImpl(resolver).readSince(lastTimestamp)
            }

            val ignorePatterns = db.userMlOverrideDao().getIgnorePatterns()
                .map { Regex(it) }


            // ============================================================
            // CLASSIFY ALL RAW SMS (no DB write yet)
            // ============================================================
            val classified = rawList.mapNotNull { sms ->

                val bodyLower = sms.body.lowercase()

                if (ignorePatterns.any { it.containsMatchIn(bodyLower) }) {
                    Log.w("expense", "IGNORED BY RULE — ${sms.body}")
                    return@mapNotNull null
                }

                val amount = SmsParser.parseAmount(sms.body)
                if (amount == null || amount <= 0) return@mapNotNull null

                val result = SmsMlPipeline.classify(
                    raw = RawSms(sms.sender, sms.body, sms.timestamp),
                    parsedAmount = amount,
                    overrideProvider = { key ->
                        db.userMlOverrideDao().getValue(key)
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


            // ============================================================
            // SAVE EACH SMS TO DB AND CALL LINKED-DETECTOR
            // ============================================================
            withContext(Dispatchers.IO) {

                for (entity in classified) {

                    // 1) insert row
                    val id = db.smsDao().insert(entity)

                    // 2) read back saved row with all defaults (id, link fields etc.)
                    val saved = db.smsDao().getById(id) ?: continue

                    // 3) convert entity -> domain model
                    val coreModel = saved.toDomain()

                    // 4) run linked-transaction detector (clean architecture)
                    linkedDetector.process(coreModel)
                }
            }

            // ============================================================
            // RETURN LIVE FLOW OF ALL SMS
            // ============================================================
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

        Log.d("expense", "Saving override: $key -> $newMerchant")

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
                if (v != null) {
                    Log.d("expense", "exp key=$key => $v")
                }
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

        Log.d("expense", "Normalized merchant=$normMerchant, sender=$normSender")

        val result = SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = parsedAmount,
            overrideProvider = { key ->

                // 1) Exact key
                db.userMlOverrideDao().getValue(key)?.also {
                    Log.d("expense", "match key=$key => $it")
                    return@classify it
                }

                // 2) merchant:<normalizedMerchant>
                db.userMlOverrideDao().getValue("merchant:$normMerchant")?.also {
                    Log.d("expense", "match merchant:$normMerchant => $it")
                    return@classify it
                }

                // 3) merchant:<normalizedSender>
                db.userMlOverrideDao().getValue("merchant:$normSender")?.also {
                    Log.d("expense", "match merchant:$normSender => $it")
                    return@classify it
                }

                Log.d("expense", "NO MATCH for key=$key")
                null
            }
        ) ?: return null

        val updated = tx.copy(
            merchant = result.merchant,
            category = result.category.name,
            type = if (result.isCredit) "CREDIT" else "DEBIT"
        )

        Log.d("expense", "Updated merchant=${updated.merchant}")

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

    suspend fun getAllOnce(): List<SmsEntity> {
        return db.smsDao().getAllOnce()
    }

}
