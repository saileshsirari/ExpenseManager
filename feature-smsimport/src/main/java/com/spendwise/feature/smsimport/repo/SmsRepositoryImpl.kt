package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import com.spendwise.core.linked.LinkedTransactionDetector
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.IgnorePatternBuilder
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SenderType
import com.spendwise.core.transfer.InternalTransferDetector
import com.spendwise.domain.SmsRepository
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.feature.smsimport.SmsParser
import com.spendwise.feature.smsimport.SmsReaderImpl
import com.spendwise.feature.smsimport.data.AppDatabase
import com.spendwise.feature.smsimport.data.ImportEvent
import com.spendwise.feature.smsimport.data.SmsDao.SelfRecipientEntity
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.data.UserMlOverride
import com.spendwise.feature.smsimport.data.isWalletMerchantSpend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import com.spendwise.core.Logger as Log

class SmsRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val linkedDetector: LinkedTransactionDetector
) : SmsRepository {

    private fun resolveMerchant(
        detectedMerchant: String?,
        category: CategoryType
    ): String? {
        return when {
            detectedMerchant.isNullOrBlank() &&
                    category == CategoryType.TRANSFER ->
                "Account Transfer"

            else -> detectedMerchant
        }
    }

    suspend fun importIncremental(
        resolverProvider: () -> ContentResolver
    ): Flow<ImportEvent> = flow {

        val lastTs = db.smsDao().getLastTimestamp() ?: 0L
        val resolver = resolverProvider()

        Log.d("expense", "Incremental import since $lastTs")

        val rawList = withContext(Dispatchers.IO) {
            SmsReaderImpl(resolver).readSince(lastTs + 1000)
        }

        if (rawList.isEmpty()) {
            emit(ImportEvent.Finished(db.smsDao().getAllOnce()))
            return@flow
        }

        for (sms in rawList) {
            val amount = SmsParser.parseAmount(sms.body) ?: continue

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
                sms.body.contains("wallet", true) &&
                        sms.body.contains("paid", true)

            val entity = SmsEntity(
                sender = sms.sender,
                body = sms.body,
                timestamp = sms.timestamp,
                amount = result.amount,
                merchant = resolveMerchant(
                    detectedMerchant = result.merchant,
                    category = result.category
                ),

                type = if (result.isCredit) "CREDIT" else "DEBIT",
                category = result.category.name,
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

            val id = db.smsDao().insert(entity)
            if (id <= 0) continue

            val saved = db.smsDao().getById(id) ?: continue

            // 🔒 NEVER run linker for wallet spends
            if (!saved.isNetZero && !saved.isWalletMerchantSpend()) {
                linkedDetector.process(saved.toDomain())
            }
        }

        emit(ImportEvent.Finished(db.smsDao().getAllOnce()))
    }

    // ------------------------------------------------------------
    // IMPORT ALL
    // ------------------------------------------------------------
    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<ImportEvent> =
        flow {
            val resolver = resolverProvider()

            // -----------------------------
            // READ ONLY LAST FEW MONTHS
            // -----------------------------
            val now = LocalDate.now()
            val monthStart = LocalDate.now().minusYears(5).withDayOfMonth(1)
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

                if (ignorePatterns.any { it.containsMatchIn(bodyLower) }) {
                    continue
                }

                val amount = SmsParser.parseAmount(sms.body)
                if (amount == null || amount <= 0) continue
                if(amount.toInt() ==1111){
                    Log.d("expense", "here import $bodyLower")
                }

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
                    merchant = resolveMerchant(
                        detectedMerchant = result.merchant,
                        category = result.category
                    ),
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

    suspend fun reclassifyAllWithProgress(
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        val all = db.smsDao().getAllOnce()
        val total = all.size

        all.forEachIndexed { index, tx ->
            reclassifySingle(tx.id)   // ✅ FIX: pass ID
            onProgress(index + 1, total)
        }
    }


    // ------------------------------------------------------------
    // RECLASSIFY SINGLE  (FULLY FIXED)
    // ------------------------------------------------------------
    override suspend fun reclassifySingle(id: Long): SmsEntity? {
        val tx = db.smsDao().getById(id) ?: return null

        // 🔒 Preserve user-declared self transfer
        val isUserSelfTransfer =
            tx.isNetZero && tx.linkType == "USER_SELF"

        val parsedAmount = SmsParser.parseAmount(tx.body) ?: return null

        val normMerchant = MerchantExtractorMl.normalize(tx.merchant ?: tx.sender ?: "")
        val normSender = MerchantExtractorMl.normalize(tx.sender ?: "")

        Log.d("expense", "Normalized merchant=$normMerchant, sender=$normSender")

        val result = SmsMlPipeline.classify(
            raw = RawSms(tx.sender, tx.body, tx.timestamp),
            parsedAmount = parsedAmount,
            overrideProvider = { key ->
                db.userMlOverrideDao().getValue(key)
                    ?: db.userMlOverrideDao().getValue("merchant:$normMerchant")
                    ?: db.userMlOverrideDao().getValue("merchant:$normSender")
            },
            selfRecipientProvider = {
                getSelfRecipients()
            }
        ) ?: return null

        // 🔒 Re-run INTERNAL TRANSFER detection (system truth)
        val internalResult =
            InternalTransferDetector.detect(
                senderType = SenderType.BANK,   // tx.sender is BANK SMS here
                body = tx.body,
                amount = parsedAmount,
                selfRecipientProvider = { getSelfRecipients() }
            )

        val updated = tx.copy(
            merchant = resolveMerchant(
                detectedMerchant = result.merchant,
                category = result.category
            ),

            category = result.category.name,
            type = if (result.isCredit) "CREDIT" else "DEBIT",

            // 🔒 INTERNAL TRANSFER MERGE (FINAL)
            isNetZero =
                if (isUserSelfTransfer)
                    true
                else
                    internalResult != null,

            linkType =
                if (isUserSelfTransfer)
                    tx.linkType
                else if (internalResult != null)
                    "INTERNAL_TRANSFER"
                else
                    null,

            linkId =
                if (isUserSelfTransfer)
                    tx.linkId
                else
                    null,

            linkConfidence =
                if (isUserSelfTransfer)
                    tx.linkConfidence
                else if (internalResult != null)
                    2
                else
                    0
        )

        db.smsDao().update(updated)
        Log.d("expense", "Reclassified tx=$id merchant=${updated.merchant}")

        return updated
    }



    // ------------------------------------------------------------
    // GET ALL
    // ------------------------------------------------------------
    override fun getAll(): Flow<List<SmsEntity>> {
        return db.smsDao().getAll()
    }

    suspend fun saveManualExpense(
        amount: Double,
        merchant: String,
        category: CategoryType,
        date: LocalDate,
        note: String
    ) {
        db.smsDao().insert(
            SmsEntity(
                sender = "MANUAL", // source
                body = note,
                timestamp = date
                    .atTime(LocalTime.now())   // 👈 NOT startOfDay
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),

                amount = amount,
                merchant = merchant,

                // 🔒 Direction, not source
                type = "DEBIT",
                category = category.name,

                // 🔒 Explicit safety
                isNetZero = false,
                linkType = null,
                linkId = null,
                linkConfidence = 0,
                isIgnored = false,

                updatedAt = System.currentTimeMillis()
            )
        )
        val count = db.smsDao().getAllOnce().size
        Log.e("MANUAL_EXPENSE", "DB size after manual insert = $count")

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

        // 🔒 1️⃣ Save self-recipient ONLY if merchant exists
        tx.merchant?.let {
            val person = MerchantExtractorMl.normalize(it)
            db.smsDao().insertSelfRecipient(
                SelfRecipientEntity(person)
            )
        }

        // 🔒 2️⃣ Always update THIS transaction
        val updated = tx.copy(
            category = CategoryType.TRANSFER.name,
            isNetZero = true,
            linkType = "USER_SELF",          // IMPORTANT: distinguish from auto
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
