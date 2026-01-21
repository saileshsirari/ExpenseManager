package com.spendwise.feature.smsimport.repo

import SmsMlPipeline
import android.content.ContentResolver
import com.spendwise.core.com.spendwise.core.ExpenseFrequency
import com.spendwise.core.com.spendwise.core.detector.CATEGORY_INVESTMENT
import com.spendwise.core.com.spendwise.core.detector.ClearingEntityInvestmentDetector
import com.spendwise.core.com.spendwise.core.detector.LINK_TYPE_INVESTMENT_OUTFLOW
import com.spendwise.core.com.spendwise.core.isCardBillPayment
import com.spendwise.core.com.spendwise.core.isSystemInfoDebit
import com.spendwise.core.com.spendwise.core.isWalletCredit
import com.spendwise.core.detector.InvestmentOutflowDetector
import com.spendwise.core.linked.LinkedTransactionDetector
import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.IgnorePatternBuilder
import com.spendwise.core.ml.MerchantExtractorMl
import com.spendwise.core.ml.MlReasonBundle
import com.spendwise.core.ml.RawSms
import com.spendwise.core.ml.SenderType
import com.spendwise.core.transfer.InternalTransferDetector
import com.spendwise.domain.SmsRepository
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SmsTemplateMatcher
import com.spendwise.domain.com.spendwise.feature.smsimport.data.SmsTemplateUtil
import com.spendwise.domain.com.spendwise.feature.smsimport.data.localDate
import com.spendwise.domain.com.spendwise.feature.smsimport.data.mapper.toDomain
import com.spendwise.domain.com.spendwise.feature.smsimport.ui.ApplyRuleProgress
import com.spendwise.feature.smsimport.SmsParser
import com.spendwise.feature.smsimport.SmsReaderImpl
import com.spendwise.feature.smsimport.data.AppDatabase
import com.spendwise.feature.smsimport.data.ImportEvent
import com.spendwise.feature.smsimport.data.LinkedPatternEntity
import com.spendwise.feature.smsimport.data.SmsDao.SelfRecipientEntity
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.data.UserMlOverride
import com.spendwise.feature.smsimport.data.hasCreditedPartyInSameSms
import com.spendwise.feature.smsimport.data.isSystemInfoDebit
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
    companion object {
        private const val PERSON_LINK_WINDOW_DAYS = 3
        private const val PERSON_LINK_MIN_AMOUNT = 10_000.0

    }

    private fun extractPerson(body: String): String? {
        val regex = Regex("(from|to)\\s+Mr\\.\\s*([A-Za-z ]+)", RegexOption.IGNORE_CASE)
        return regex.find(body)
            ?.groupValues
            ?.get(2)
            ?.trim()
            ?.lowercase()
    }


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

    fun importIncremental(
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
// 🔒 INVESTMENT INFO SMS → NEVER A TRANSACTION
            if (checkAndInsertIfIsInvestmentInfoSms(sms)) continue

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
                isNetZero = false,
                linkType = null,
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
// 🔒 RUN CANONICAL CLASSIFICATION PIPELINE
            val classified = classifyAndLinkSingle(saved)
            if (classified.linkType == LINK_TYPE_INVESTMENT_OUTFLOW) {
                continue
            }
// 🔒 NEVER run linker for wallet spends
            if (!classified.isNetZero && !classified.isWalletMerchantSpend()) {
                linkedDetector.process(
                    classified.toDomain(),
                    selfRecipientProvider = { getSelfRecipients() })
            }
        }
        emit(ImportEvent.Finished(db.smsDao().getAllOnce()))
    }

    // ------------------------------------------------------------
    // IMPORT ALL
    // ------------------------------------------------------------
    override suspend fun importAll(
        resolverProvider: () -> ContentResolver
    ): Flow<ImportEvent> = flow {
      Log.enabled =false
        val resolver = resolverProvider()

        val monthStart = LocalDate.now()
            .minusYears(5)
            .withDayOfMonth(1)

        val sinceMillis =
            monthStart
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond() * 1000

        Log.w("expense", "Importing SMS since $monthStart ($sinceMillis)")

        // ------------------------------------------------------------
        // READ SMS ONCE
        // ------------------------------------------------------------
        val rawList = withContext(Dispatchers.IO) {
            SmsReaderImpl(resolver).readSince(sinceMillis)
        }

        val total = rawList.size
        Log.w("expense", "Total SMS read = $total")

        // ------------------------------------------------------------
        // PRELOAD DATA (CRITICAL OPTIMIZATION)
        // ------------------------------------------------------------
        val ignorePatterns = db.userMlOverrideDao()
            .getIgnorePatterns()
            .map { Regex(it) }

        val linkedTemplates = db.smsDao().getAllLinkedPatterns()

        var processed = 0

        // ------------------------------------------------------------
        // MAIN LOOP (NO EXTRA DB LOOKUPS)
        // ------------------------------------------------------------
        for (sms in rawList) {
            processed++

            val bodyLower = sms.body.lowercase()

            // ---- ignore patterns ----
            if (ignorePatterns.any { it.containsMatchIn(bodyLower) }) {
                continue
            }

            // ---- investment INFO sms (never a transaction) ----
            if (checkAndInsertIfIsInvestmentInfoSms(sms)) continue

            val amount = SmsParser.parseAmount(sms.body)
            if (amount == null || amount <= 0) continue

            // --------------------------------------------------
            // ML CLASSIFICATION
            // --------------------------------------------------
            val result = SmsMlPipeline.classify(
                raw = RawSms(sms.sender, sms.body, sms.timestamp),
                parsedAmount = amount,
                overrideProvider = { key ->
                    db.userMlOverrideDao().getValue(key)
                },
                selfRecipientProvider = { getSelfRecipients() }
            ) ?: continue

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
                isNetZero = false,
                linkType = null,
                linkId = null,
                linkConfidence = 0
            )

            val id = db.smsDao().insert(entity)
            if (id <= 0) continue

            val saved = db.smsDao().getById(id) ?: continue

            // --------------------------------------------------
            // USER SELF TEMPLATE MATCH (FAST, IN-MEMORY)
            // --------------------------------------------------
            val normalized = SmsTemplateUtil.buildTemplate(saved.body)
            if (linkedTemplates.any { it == normalized }) {
                db.smsDao().updateLink(
                    id = saved.id,
                    linkId = null,
                    linkType = "USER_SELF",
                    linkConfidence = 100,
                    isNetZero = true
                )
                continue
            }

            // --------------------------------------------------
            // CANONICAL PIPELINE
            // --------------------------------------------------
            val classified = classifyAndLinkSingle(saved)

            // --------------------------------------------------
            // LINKING (SKIP WHEN POSSIBLE)
            // --------------------------------------------------
            if (
                classified.linkType != LINK_TYPE_INVESTMENT_OUTFLOW &&
                !classified.isNetZero &&
                !classified.isWalletMerchantSpend()
            ) {
                linkedDetector.process(
                    classified.toDomain(),
                    selfRecipientProvider = { getSelfRecipients() }
                )
            }

            // --------------------------------------------------
            // PROGRESS (THROTTLED)
            // --------------------------------------------------
            if (processed % 200 == 0 || processed == total) {
                emit(
                    ImportEvent.Progress(
                        total = total,
                        processed = processed,
                        message = "Processing messages"
                    )
                )
            }
        }
        Log.enabled = true

        emit(ImportEvent.Finished(db.smsDao().getAllOnce()))
    }


    private suspend fun checkAndInsertIfIsInvestmentInfoSms(sms: RawSms): Boolean {
        if (isInvestmentInfoSms(sms.body)) {
            db.smsDao().insert(
                SmsEntity(
                    sender = sms.sender,
                    body = sms.body,
                    timestamp = sms.timestamp,
                    amount = 0.0,
                    merchant = null,
                    type = "INFO",
                    category = CategoryType.INVESTMENT.name,
                    isNetZero = true,
                    linkType = null,
                    linkId = null,
                    linkConfidence = 0
                )
            )
            return true
        }
        return false

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
    // ------------------------------------------------------------
// CATEGORY OVERRIDE  (ENUM-SAFE)
// ------------------------------------------------------------
    suspend fun changeMerchantCategory(
        merchant: String,
        newCategory: CategoryType
    ) {
        val key = MerchantCategoryOverride.keyFor(merchant)

        Log.d("expense", "Category override: $key -> ${newCategory.name}")

        // 1️⃣ Save override (future imports & reprocess)
        db.userMlOverrideDao().save(
            UserMlOverride(
                key = key,
                value = MerchantCategoryOverride.encode(newCategory)
            )
        )

        // 2️⃣ Update existing transactions immediately
        db.smsDao().updateCategoryForMerchant(
            merchant = merchant,
            category = newCategory.name
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

    private fun isInvestmentInfoSms(body: String): Boolean {
        val lower = body.lowercase()

        return listOf(
            "units has been processed",
            "nav of",
            "folio",
            "account statement will be sent",
            "your purchase of",
            "units allotted",
            "fund -",
            "growth option"
        ).any { lower.contains(it) }
    }

    suspend fun reprocessSingleSms(id: Long): SmsEntity? {
        val tx = db.smsDao().getById(id) ?: return null

        Log.e("REPROCESS", "==============================")
        Log.e("REPROCESS", "START id=${tx.id}")
        Log.e("REPROCESS", tx.body)

        // 1️⃣ Reset derived fields (VERY IMPORTANT)
        val reset = tx.copy(
            merchant = null,
            category = null,
            type = null,
            isNetZero = false,
            linkId = null,
            linkType = null,
            linkConfidence = 0
        )

        db.smsDao().update(reset)

        // 2️⃣ Run full pipeline
        return classifyAndLinkSingle(reset)
    }

    val DAY_MS = 24L * 60L * 60L * 1000L

    private fun isFdConfirmationSms(body: String): Boolean {
        val text = body.lowercase()
        return listOf(
            "fd no",
            "fd number",
            "fd account number",
            "your fd has been created",
            "fd created successfully",
            "term deposit account",
            "fixed deposit account"
        ).any { text.contains(it) }
    }

    private suspend fun classifyAndLinkSingle(original: SmsEntity): SmsEntity {

        val body = original.body
        val amount = SmsParser.parseAmount(body) ?: 0.0
        var tx = original   // ✅ mutable working variable
        val templates = db.smsDao().getAllLinkedPatterns()
        for (tpl in templates) {
            if (SmsTemplateMatcher.matches(tx.body, tpl)) {
                val updated = tx.copy(
                    isNetZero = true,
                    linkType = "USER_SELF",
                    linkConfidence = 100
                )
                db.smsDao().update(updated)
                return updated   // 🔒 HARD STOP
            }
        }

// --------------------------------------------------
// INFO / SYSTEM ROUTING DEBIT (LOCKED RULE)
        if (tx.isSystemInfoDebit()) {
            val internal =
                InternalTransferDetector.detect(
                    senderType = SenderType.BANK,
                    body = tx.body,
                    amount = amount,
                    selfRecipientProvider = { getSelfRecipients() }
                )
            if (internal != null) {
                val updated = tx.copy(
                    type = "DEBIT",
                    isNetZero = true,
                    linkType = "INTERNAL_TRANSFER",
                    linkConfidence = 95
                )
                db.smsDao().update(updated)

                Log.d(
                    "SYSTEM_INFO",
                    "Info/system debit detected → INTERNAL_TRANSFER, id=${tx.id}"
                )

                return updated
            }
        }

// INVESTMENT OUTFLOW DETECTION (BODY-BASED, SAFE)
        if (
            tx.hasCreditedPartyInSameSms() &&
            InvestmentOutflowDetector.isInvestmentOutflow(tx.body)
        ) {
            val updated = tx.copy(
                linkType = LINK_TYPE_INVESTMENT_OUTFLOW,
                category = CATEGORY_INVESTMENT,
                type = "DEBIT",
                isNetZero = false,
                expenseFrequency = ExpenseFrequency.YEARLY.name
            )

            db.smsDao().update(updated)

            Log.d(
                "INVESTMENT",
                "Detected INVESTMENT_OUTFLOW → YEARLY default, id=${tx.id}"
            )

            return updated
        }

        Log.e("REPROCESS", "Parsed amount=$amount")

        // 🔒 1️⃣ INFO / system SMS
        if (isInvestmentInfoSms(body) || isSystemInfoDebit(body)) {
            Log.e("REPROCESS", "INFO SMS detected")

            val updated = tx.copy(
                type = "INFO",
                isNetZero = true
            )
            db.smsDao().update(updated)
            return updated
        }

        // 🔒 2️⃣ Credit-card bill payment
        if (isCardBillPayment(body)) {
            Log.e("REPROCESS", "CREDIT CARD BILL detected")

            val updated = tx.copy(
                type = "DEBIT",
                category = CategoryType.CREDIT_CARD_PAYMENT.name,
                isNetZero = true
            )
            db.smsDao().update(updated)
            return updated
        }

        // 🔒 3️⃣ Wallet credit (top-up)
        if (isWalletCredit(body)) {
            Log.e("REPROCESS", "WALLET CREDIT detected")

            val updated = tx.copy(
                type = "CREDIT",
                isNetZero = true
            )
            db.smsDao().update(updated)
            return updated
        }


// --------------------------------------------------
// CLEARING ENTITY INVESTMENT (NEW RULE)
// --------------------------------------------------
        if (
            ClearingEntityInvestmentDetector.isClearingEntityInvestment(
                senderType = SenderType.BANK,
                txType = tx.type,            // 👈 explicit
                body = tx.body,
                amount = amount
            )
        ) {
            val updated = tx.copy(
                type = "DEBIT",
                category = CategoryType.INVESTMENT.name,
                isNetZero = false,
                expenseFrequency = ExpenseFrequency.YEARLY.name,
                linkType = "INVESTMENT_OUTFLOW",
                linkConfidence = 70   // medium confidence
            )

            db.smsDao().update(updated)

            Log.d(
                "INVESTMENT",
                "Clearing-entity investment detected → YEARLY (pending confirmation), id=${tx.id}"
            )

            return updated
        }

        // 🔒 4️⃣ ML classification (LAST)
        val result = SmsMlPipeline.classify(
            raw = RawSms(tx.sender, body, tx.timestamp),
            parsedAmount = amount,
            selfRecipientProvider = { getSelfRecipients() },
            overrideProvider = { key ->
                val v = db.userMlOverrideDao().getValue(key)
                if (v != null) {
                    Log.d("expense", "exp key=$key => $v")
                }
                v
            }
        )

        if (result != null) {
            Log.e("REPROCESS", "ML result=$result")

            val overrideCategory =
                MerchantCategoryOverride.decode(
                    db.userMlOverrideDao().getValue(
                        MerchantCategoryOverride.keyFor(
                            result.merchant ?: tx.merchant ?: ""
                        )
                    )
                )


            val finalCategory =
                overrideCategory ?: result.category

            val updated = tx.copy(
                type = if (result.isCredit) "CREDIT" else "DEBIT",
                category = if (tx.category == CategoryType.INVESTMENT.name &&
                    tx.expenseFrequency == ExpenseFrequency.MONTHLY.name
                ) {
                    ExpenseFrequency.YEARLY.name
                } else finalCategory.name,
                merchant = resolveMerchant(result.merchant, finalCategory)
            )

            db.smsDao().update(updated)

            // 🔒 5️⃣ Internal transfer detector (AFTER ML)
            linkedDetector.process(
                updated.toDomain(),
                selfRecipientProvider = { getSelfRecipients() })

            return db.smsDao().getById(tx.id)!!
        }

        Log.e("REPROCESS", "FALLTHROUGH — no rule matched")
        return tx
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

        // --------------------------------------------------
// CLEARING ENTITY INVESTMENT (REPROCESS PATH)
// --------------------------------------------------
        val isClearingInvestment =
            ClearingEntityInvestmentDetector.isClearingEntityInvestment(
                senderType = SenderType.BANK,
                txType = if (result.isCredit) "CREDIT" else "DEBIT",
                body = tx.body,
                amount = parsedAmount
            )


        val updated = tx.copy(
            merchant = resolveMerchant(
                detectedMerchant = result.merchant,
                category = result.category
            ),

            category =
                when {
                    isClearingInvestment -> CategoryType.INVESTMENT.name
                    else -> result.category.name
                },

            expenseFrequency =
                if (isClearingInvestment)
                    ExpenseFrequency.YEARLY.name
                else
                    tx.expenseFrequency,

            isNetZero =
                when {
                    isUserSelfTransfer -> true
                    isClearingInvestment -> false
                    else -> internalResult != null
                },

            linkType =
                when {
                    isUserSelfTransfer -> tx.linkType
                    isClearingInvestment -> LINK_TYPE_INVESTMENT_OUTFLOW
                    internalResult != null -> "INTERNAL_TRANSFER"
                    else -> null
                },

            linkConfidence =
                when {
                    isUserSelfTransfer -> tx.linkConfidence
                    isClearingInvestment -> 70
                    internalResult != null -> 2
                    else -> 0
                },

            linkId =
                if (isUserSelfTransfer)
                    tx.linkId
                else
                    null,
        )

        db.smsDao().update(updated)
        Log.d("expense", "Reclassified tx=$id merchant=${updated.merchant}")

// 🔒 VERY IMPORTANT: re-run linker if eligible
        if (
            updated.linkType != LINK_TYPE_INVESTMENT_OUTFLOW &&
            !updated.isNetZero &&
            !updated.isWalletMerchantSpend()
        ) {
            linkedDetector.process(
                updated.toDomain(),
                selfRecipientProvider = { getSelfRecipients() }
            )
        }
        Log.d("expense", "Reclassified tx=$id merchant=${updated.merchant}")
        return db.smsDao().getById(id)

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
            linkConfidence = 90
        )

        db.smsDao().update(updated)
    }

    fun applySelfTransferPattern(
        seedTx: SmsEntity
    ): Flow<ApplyRuleProgress> = flow {

        val smsDao = db.smsDao()

        // 1️⃣ Build structural template
        val template = SmsTemplateUtil.buildTemplate(seedTx.body)

        // 2️⃣ Store template (idempotent)
        smsDao.insertPattern(
            LinkedPatternEntity(pattern = template)
        )

        // 3️⃣ Find candidate SMS
        val candidates = smsDao.getAllDebitNonNetZero()
        val total = candidates.size

        var done = 0
        emit(ApplyRuleProgress(done = 0, total = total))

        // 4️⃣ Apply USER_SELF
        candidates.forEach { tx ->
            if (SmsTemplateMatcher.matches(tx.body, template)) {
                smsDao.updateLink(
                    id = tx.id,
                    linkId = null,
                    linkType = "USER_SELF",
                    linkConfidence = 100,
                    isNetZero = true
                )
            }

            done++
            if (done % 25 == 0 || done == total) {
                emit(ApplyRuleProgress(done = done, total = total))
            }
        }
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

    suspend fun setExpenseFrequency(
        tx: SmsEntity,
        frequency: ExpenseFrequency
    ) {
        val anchorYear =
            when (frequency) {
                ExpenseFrequency.YEARLY ->
                    tx.localDate().year

                else -> null
            }

        db.smsDao().updateExpenseFrequency(
            id = tx.id,
            frequency = frequency.name,
            anchorYear = anchorYear
        )
    }


}
