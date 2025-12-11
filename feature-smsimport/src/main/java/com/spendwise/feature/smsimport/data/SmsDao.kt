
package com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface SmsDao {

    // IMPORTANT: Insert must return rowId for linking logic
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<SmsEntity>)

    @Query("SELECT * FROM sms ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsEntity>>

    @Query("SELECT MAX(timestamp) FROM sms")
    suspend fun getLastTimestamp(): Long?

    @Query("SELECT * FROM sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsEntity?

    @Update
    suspend fun update(item: SmsEntity)

    @Query("SELECT * FROM sms")
    suspend fun getAllOnce(): List<SmsEntity>

    @Query("UPDATE sms SET isIgnored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Int)

    @Query("""
        SELECT * FROM sms 
        WHERE amount = :amount 
          AND timestamp BETWEEN :from AND :to 
          AND id != :excludeId
    """)
    suspend fun findByAmountAndDateRange(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<SmsEntity>

    @Query("""
        UPDATE sms SET 
            linkId = :linkId,
            linkType = :linkType,
            linkConfidence = :linkConfidence,
            isNetZero = :isNetZero
        WHERE id = :id
    """)
    suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        linkConfidence: Int,
        isNetZero: Boolean
    )

    @Query("""
    SELECT * FROM sms
    WHERE linkId IS NOT NULL
""")
    suspend fun getAllLinked(): List<SmsEntity>

}
