package com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    // ======================================================
    // SMS TABLE
    // ======================================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<SmsEntity>)

    @Query("SELECT * FROM sms ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms")
    suspend fun getAllOnce(): List<SmsEntity>

    @Query("SELECT MAX(timestamp) FROM sms")
    suspend fun getLastTimestamp(): Long?

    @Query("SELECT * FROM sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsEntity?

    @Update
    suspend fun update(item: SmsEntity)

    @Query("DELETE FROM sms")
    suspend fun deleteAll()

    @Query("UPDATE sms SET isIgnored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Int)

    @Query(
        """
        SELECT * FROM sms
        WHERE amount = :amount
          AND timestamp BETWEEN :from AND :to
          AND id != :excludeId
    """
    )
    suspend fun findByAmountAndDateRange(
        amount: Double,
        from: Long,
        to: Long,
        excludeId: Long
    ): List<SmsEntity>

    @Query(
        """
        UPDATE sms SET
            linkId = :linkId,
            linkType = :linkType,
            linkConfidence = :linkConfidence,
            isNetZero = :isNetZero
        WHERE id = :id
    """
    )
    suspend fun updateLink(
        id: Long,
        linkId: String?,
        linkType: String?,
        linkConfidence: Int,
        isNetZero: Boolean
    )

    @Query("SELECT * FROM sms WHERE linkId IS NOT NULL")
    suspend fun getAllLinked(): List<SmsEntity>


    // ======================================================
    // LINKED PATTERN TABLE
    // ======================================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPattern(entity: LinkedPatternEntity)

    @Query("SELECT pattern FROM linked_patterns")
    suspend fun getAllLinkedPatterns(): List<String>

    @Query(
        """
        SELECT pattern FROM linked_patterns
        WHERE pattern LIKE '%|transferred_to'
           OR pattern LIKE '%|sent_to'
           OR pattern LIKE '%|person_transfer'
    """
    )
    suspend fun getAllLinkedDebitPatterns(): List<String>

    @Query(
        """
        SELECT pattern FROM linked_patterns
        WHERE pattern LIKE '%|deposit_from'
           OR pattern LIKE '%|credited_to'
           OR pattern LIKE '%|person_transfer'
    """
    )
    suspend fun getAllLinkedCreditPatterns(): List<String>


    @Query(
        """
        UPDATE sms
        SET 
            isIgnored = :isIgnored,
            ignoreReason = :reason,
            updatedAt = strftime('%s','now') * 1000
        WHERE id = :id
    """
    )
    suspend fun updateIgnore(
        id: Long,
        isIgnored: Boolean,
        reason: String
    )

    // ------------------------------------------------------------------
    // Update merchant (used for wallet spends correction)
    // ------------------------------------------------------------------
    @Query("""
        UPDATE sms
        SET 
            merchant = :merchant,
            updatedAt = strftime('%s','now') * 1000
        WHERE id = :id
    """)
    suspend fun updateMerchant(
        id: Long,
        merchant: String
    )

    @Entity(tableName = "self_recipients")
    data class SelfRecipientEntity(
        @PrimaryKey val normalizedName: String,
        val createdAt: Long = System.currentTimeMillis()
    )


    // ======================================================
// SELF RECIPIENT RULES (USER DECLARED)
// ======================================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSelfRecipient(entity: SelfRecipientEntity)

    @Query("SELECT normalizedName FROM self_recipients")
    suspend fun getAllSelfRecipients(): List<String>

    @Query("DELETE FROM self_recipients WHERE normalizedName = :name")
    suspend fun deleteSelfRecipient(name: String)


}
