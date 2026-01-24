package com.spendwise.domain

import android.content.ContentResolver
import com.spendwise.feature.smsimport.data.ImportEvent
import com.spendwise.feature.smsimport.data.SmsEntity
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
     fun importAll(resolverProvider: () -> ContentResolver): Flow<ImportEvent>
     suspend fun saveManual(sender: String, body: String, timestamp: Long)
    suspend fun reclassifySingle(id: Long): SmsEntity?
    fun getAll(): Flow<List<SmsEntity>>
    suspend fun markIgnored(tx: SmsEntity)
    suspend fun setIgnored(lng: Long, bool: Boolean)
    suspend fun loadExisting(): List<SmsEntity>

}
