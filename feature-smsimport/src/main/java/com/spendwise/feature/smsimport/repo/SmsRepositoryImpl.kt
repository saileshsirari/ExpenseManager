package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import android.os.Build
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
import com.spendwise.feature.smsimport.data.ImportEvent
import com.spendwise.feature.smsimport.data.SmsDao.SelfRecipientEntity
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.data.UserMlOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.spendwise.core.Logger as Log

class SmsRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val linkedDetector: LinkedTransactionDetector
) : SmsRepository {


    // ------------------------------------------------------------
    // IMPORT ALL
    // ------------------------------------------------------------
    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<ImportEvent> =
        flow {
            db.smsDao().deleteAll()

            val resolver = resolverProvider()

            // -----------------------------
            // READ ONLY LAST FEW MONTHS
            // -----------------------------
            val now = LocalDate.now()
            val monthStart = now.minusMonths(3).withDayOfMonth(1)
            val monthStartMillis = monthStart
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond() * 1000

            Log.w("expense", "Importing SMS since: $monthStart  ($monthStartMillis)")


            // -----------------------------
            // READ RAW SMS
            // -----------------------------
            val rawList = withContext(Dispatchers.IO) {
                SmsReaderImpl(resolver).readSince(monthStartMillis)
            }

            val ignorePatterns = db.userMlOverrideDao()
                .getIgnorePatterns()
                .map { Regex(it) }

            val total = rawList.size
            var processed = 0

            Log.w("expense", "Total SMS read: $total")


            // ============================================================
            // CLASSIFY & SAVE EACH SMS WITH PROGRESS UPDATES
            // ============================================================
            val classifiedEntities = mutableListOf<SmsEntity>()

            for (sms in rawList) {

                processed++

                // ---- Progress update event ----
                emit(
                    ImportEvent.Progress(
                        total = total,
                        processed = processed,
                        message = "Processing $processed of $total messages"
                    )
                )

                // ---- ignore patterns ----
                val bodyLower = sms.body.lowercase()


// 🔥 HARD IGNORE — credit card spends
              /*  if (isCreditCardSpend(bodyLower)) {
                    Log.d("expense", "IMPORT SKIP — Credit card spend")
                    continue
                }*/

// User-defined ignore patterns
                if(bodyLower.contains("olamoney")) {
                    Log.d("expense", "here import $bodyLower")
                }
                if (ignorePatterns.any { it.containsMatchIn(bodyLower) }) {
                    continue
                }


                val amount = SmsParser.parseAmount(sms.body)
                if (amount == null || amount <= 0) continue

                val result = SmsMlPipeline.classify(
                    raw = RawSms(sms.sender, sms.body, sms.timestamp),
                    parsedAmount = amount,
                    overrideProvider = { key ->
                        db.userMlOverrideDao().getValue(key)
                    },
                    selfRecipientProvider = {
                        getSelfRecipients()
                    }
                ) ?: continue
                val isWalletSpend =
                    sms.body.contains("wallet", ignoreCase = true) &&
                            sms.body.contains("paid", ignoreCase = true)

                val entity = SmsEntity(
                    sender = result.rawSms.sender,
                    body = result.rawSms.body,
                    timestamp = result.rawSms.timestamp,
                    amount = result.amount,
                    merchant = result.merchant,
                    type = if (result.isCredit) "CREDIT" else "DEBIT",
                    category = result.category.name,

                    // 🔒 APPLY INTERNAL TRANSFER DECISION HERE
                    // 🔒 WALLET SPEND OVERRIDES INTERNAL TRANSFER
                    isNetZero =
                        if (isWalletSpend) false else result.isSingleSmsInternal,

                    linkType =
                        if (isWalletSpend) null
                        else if (result.isSingleSmsInternal) "INTERNAL_TRANSFER"
                        else null,

                    linkId =
                        if (isWalletSpend) null
                        else if (result.isSingleSmsInternal) "single:${sms.timestamp}"
                        else null,

                    linkConfidence =
                        if (isWalletSpend) 0
                        else if (result.isSingleSmsInternal) 2
                        else 0

                )
                classifiedEntities.add(entity)

            }


            // ============================================================
            // WRITE CLASSIFIED ENTITIES TO DB AND RUN LINKED DETECTOR
            // ============================================================
            withContext(Dispatchers.IO) {
                for (entity in classifiedEntities) {

                    // 1) Insert raw classified entity
                    // 1) Insert raw classified entity
                    val id = db.smsDao().insert(entity)

// 2) Read inserted row
                    val saved = db.smsDao().getById(id) ?: continue
                    val isWalletSpend =
                        saved.body.contains("wallet", ignoreCase = true) &&
                                saved.body.contains("paid", ignoreCase = true)
                    // 🔒 NEVER run linker for wallet spends
                    if (!saved.isNetZero && !isWalletSpend) {
                        linkedDetector.process(saved.toDomain())
                    }

                }
            }


            // ============================================================
            // RETURN FINISHED EVENT WITH LIVE DATABASE CONTENT
            // ============================================================
            val finalList = db.smsDao().getAllOnce() // you can create this function
            emit(ImportEvent.Finished(finalList))
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
            },
            selfRecipientProvider = {
                getSelfRecipients()
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
            },
            selfRecipientProvider = {
                getSelfRecipients()
            }
        ) ?: return null

        val updated = tx.copy(
            merchant = result.merchant,
            category = result.category.name,
            type = if (result.isCredit) "CREDIT" else "DEBIT",

            // 🔒 FIX: preserve / re-apply internal transfer truth
            // 🔒 Preserve existing internal-transfer truth ONLY
            isNetZero = tx.isNetZero,
            linkType = tx.linkType,
            linkId = tx.linkId,
            linkConfidence = tx.linkConfidence
        )

        db.smsDao().update(updated)


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

    override suspend fun loadExisting(): List<SmsEntity> {
        return db.smsDao().getAllOnce()
    }

    suspend fun reclassifyAll() {
        val all = getAllOnce()
        all.forEach { tx ->
            reclassifySingle(tx.id)
        }
    }

    suspend fun markAsSelfTransfer(tx: SmsEntity) {

        // 1️⃣ Normalize person name
        val person = MerchantExtractorMl.normalize(tx.merchant ?: return)

        // 2️⃣ Save self-recipient rule
        db.smsDao().insertSelfRecipient(
            SelfRecipientEntity(person)
        )

        // 3️⃣ Update THIS transaction immediately
        val updated = tx.copy(
            category = CategoryType.TRANSFER.name,
            isNetZero = true,
            linkType = "INTERNAL_TRANSFER",
            linkId = tx.id.toString(),
            linkConfidence = 1
        )

        db.smsDao().update(updated)
    }
    suspend fun getSelfRecipients(): Set<String> {
        return db.smsDao()
            .getAllSelfRecipients()
            .map { MerchantExtractorMl.normalize(it) }
            .toSet()
    }

    suspend fun undoSelfTransfer(tx: SmsEntity) {

        // 1️⃣ Revert THIS transaction
        val reverted = tx.copy(
            category = CategoryType.PERSON.name,
            isNetZero = false,
            linkType = null,
            linkId = null,
            linkConfidence = 0
        )

        db.smsDao().update(reverted)

        // 2️⃣ OPTIONAL (recommended UX):
        // remove self-recipient rule ONLY if user wants global undo
        val person = tx.merchant?.let { MerchantExtractorMl.normalize(it) }
        if (person != null) {
            db.smsDao().deleteSelfRecipient(person)
        }
    }



}
