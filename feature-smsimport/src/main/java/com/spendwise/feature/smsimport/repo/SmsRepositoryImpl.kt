package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import com.spendwise.core.ml.IgnorePatternBuilder
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
import javax.inject.Inject

class SmsRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : SmsRepository {

    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<List<SmsEntity>> =
        flow {
            val resolver = resolverProvider()

            val lastTimestamp = db.smsDao().getLastTimestamp() ?: 0L

            val rawList = withContext(Dispatchers.IO) {
                SmsReaderImpl(resolver).readSince(lastTimestamp)
            }

            // Load ignore regex list once
            val ignorePatterns = db.userMlOverrideDao().getIgnorePatterns()
                .map { Regex(it) }

            val classified = rawList.mapNotNull { sms ->

                val bodyLower = sms.body.lowercase()

                // 1️⃣ IGNORE IF matches any rule
                if (ignorePatterns.any { it.containsMatchIn(bodyLower) }) {
                    return@mapNotNull null
                }

                // 2️⃣ Extract amount
                val amount = SmsParser.parseAmount(sms.body)
                if (amount == null || amount <= 0) return@mapNotNull null

                // 3️⃣ ML classification
                val result = SmsMlPipeline.classify(
                    raw = RawSms(sms.sender, sms.body, sms.timestamp),
                    parsedAmount = amount,
                    overrideProvider = { key -> db.userMlOverrideDao().getValue(key) }
                ) ?: return@mapNotNull null

                SmsEntity(
                    sender = result.rawSms.sender,
                    body = result.rawSms.body,
                    timestamp = result.rawSms.timestamp,
                    amount = result.amount,
                    merchant = result.merchant,
                    type = if (result.isCredit) "credit" else "debit",
                    category = result.category.name
                )
            }

            if (classified.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    classified.chunked(300).forEach { chunk ->
                        db.smsDao().insertAll(chunk)
                    }
                }
            }

            emitAll(db.smsDao().getAll())
        }


    override suspend fun saveManual(sender: String, body: String, timestamp: Long) {
        val amount = SmsParser.parseAmount(body) ?: return
        db.smsDao().insert(
            SmsEntity(
                sender = sender,
                body = body,
                timestamp = timestamp,
                amount = amount,
                merchant = null,
                type = "manual",
                category = "OTHER"
            )
        )
    }
    suspend fun saveMerchantOverride(old: String, new: String) {
        val normalizedOld = old.lowercase().replace(Regex("[^a-z0-9]"), "")
        db.userMlOverrideDao().save(
            UserMlOverride("merchant:$normalizedOld", new.trim())
        )
    }

    suspend fun saveIgnorePattern(body: String) {
        val pattern = IgnorePatternBuilder.build(body)
        db.userMlOverrideDao().save(
            UserMlOverride(
                key = "ignore_pattern:${pattern.hashCode()}",
                value = pattern
            )
        )
    }

    suspend fun saveCategoryOverride(merchant: String, newCategory: String) {
        db.userMlOverrideDao().save(
            UserMlOverride("category:$merchant", newCategory)
        )
    }

    suspend fun getMlExplanationFor(tx: SmsEntity): MlReasonBundle? {
        val amount = tx.amount
        if (amount <= 0) return null

        return SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = amount,
            overrideProvider = { key -> db.userMlOverrideDao().getValue(key) }
        )?.explanation
    }
    suspend fun reclassifySingle(id: Long): SmsEntity? {
        val tx = db.smsDao().getById(id) ?: return null

        val parsedAmount = SmsParser.parseAmount(tx.body) ?: return null

        val result = SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = parsedAmount,
            overrideProvider = { key ->
                db.userMlOverrideDao().getValue(key)
            }
        ) ?: return null

        val updated = tx.copy(
            merchant = result.merchant,
            category = result.category.name,
            type = if (result.isCredit) "credit" else "debit"
        )

        db.smsDao().update(updated)
        return updated
    }

    override fun getAll(): Flow<List<SmsEntity>> {
        return db.smsDao().getAll()
    }

}
