
package com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface SmsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<SmsEntity>)

    @Query("SELECT * FROM sms ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsEntity>>

    @Query("SELECT MAX(timestamp) FROM sms")
    suspend fun getLastTimestamp(): Long?
}
