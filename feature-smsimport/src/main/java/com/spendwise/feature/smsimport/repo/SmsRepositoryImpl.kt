package com.spendwise.feature.smsimport.repo

import android.content.ContentResolver
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SmsMlPipeline
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

            // 1️⃣ Get last processed SMS timestamp from DB
            val lastTimestamp = db.smsDao().getLastTimestamp() ?: 0L

            // 2️⃣ Read only NEW SMS (incremental)
            val newRawSmsList = withContext(Dispatchers.IO) {
                SmsReaderImpl(resolver).readSince(lastTimestamp)
            }

            if (newRawSmsList.isEmpty()) {
                // Nothing new → just emit loaded DB
                emitAll(db.smsDao().getAll())
                return@flow
            }

            // 3️⃣ ML classify NEW messages only
            val classifiedList = withContext(Dispatchers.IO) {

                newRawSmsList.mapNotNull { sms ->

                    val parsedAmount = SmsParser.parseAmount(sms.body)
                    if (parsedAmount == null || parsedAmount <= 0) return@mapNotNull null

                    val result = SmsMlPipeline.classify(
                        raw = RawSms(sms.sender, sms.body, sms.timestamp),
                        parsedAmount = parsedAmount,
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
            }

            // 4️⃣ Insert only NEW classified items
            if (classifiedList.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    classifiedList.chunked(300).forEach { chunk ->
                        db.smsDao().insertAll(chunk)
                    }
                }
            }

            // 5️⃣ Emit full DB
            emitAll(db.smsDao().getAll())
        }

    override suspend fun saveManual(sender: String, body: String, timestamp: Long) {
        val amount = SmsParser.parseAmount(body) ?: return

        // Default manual entry behavior
        db.smsDao().insert(
            SmsEntity(
                sender = "USER",
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
        db.userMlOverrideDao().save(
            UserMlOverride("merchant:$old", new)
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
            raw = RawSms(
                sender = tx.sender,
                body = tx.body,
                timestamp = tx.timestamp
            ),
            parsedAmount = amount,
            overrideProvider = { key ->
                db.userMlOverrideDao().getValue(key)
            }
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
