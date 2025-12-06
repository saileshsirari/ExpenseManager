package com.spendwise.feature.smsimport.repo

import android.content.ContentResolver
import com.spendwise.domain.SmsRepository
import com.spendwise.feature.smsimport.SmsParser
import com.spendwise.feature.smsimport.SmsReaderImpl
import com.spendwise.feature.smsimport.data.AppDatabase
import com.spendwise.feature.smsimport.data.SmsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SmsRepositoryImpl @Inject constructor(private val db: AppDatabase) : SmsRepository {
    override suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<List<SmsEntity>> =
        flow {
            val resolver = resolverProvider()
            val raw = withContext(Dispatchers.IO) {
                val reader = SmsReaderImpl(resolver)
                reader.readAllSms()   // can be thousands, but now runs off main thread
            }
            withContext(Dispatchers.IO) {
                val entities = raw.mapNotNull { sms ->
                    if (!sms.body.contains("debited", ignoreCase = true) &&
                        !sms.body.contains("credited", ignoreCase = true) &&
                        !sms.body.contains("txn", ignoreCase = true)
                    ) {
                        return@mapNotNull null
                    }
                    SmsParser.parse(sms.body)?.let {
                        SmsEntity(
                            sender = sms.sender,
                            body = sms.body,
                            timestamp = sms.timestamp,
                            amount = it.amount,
                            merchant = it.merchant,
                            type = it.type
                        )
                    }
                }
                // Insert in chunks to avoid large transactions
                entities.chunked(300).forEach { chunk ->
                    db.smsDao().insertAll(chunk)
                }
            }
            emitAll(db.smsDao().getAll())
        }

    override suspend fun saveManual(sender: String, body: String, timestamp: Long) {
        val parsed = SmsParser.parse(body)
        parsed?.amount ?: return
        db.smsDao().insert(
            SmsEntity(
                sender = sender,
                body = body,
                timestamp = timestamp,
                amount = parsed?.amount ?: 0.0,
                merchant = parsed?.merchant,
                type = parsed?.type
            )
        )
    }
}
