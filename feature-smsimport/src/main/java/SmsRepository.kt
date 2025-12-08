package com.spendwise.domain

import android.content.ContentResolver
import com.spendwise.feature.smsimport.data.SmsEntity
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    suspend fun importAll(resolverProvider: () -> ContentResolver): Flow<List<SmsEntity>>
     suspend fun saveManual(sender: String, body: String, timestamp: Long)
    suspend fun reclassifySingle(id: Long): SmsEntity?
    fun getAll(): Flow<List<SmsEntity>>

}
