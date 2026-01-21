package com.spendwise.domain.com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendwise.feature.smsimport.data.UserSelfPattern

@Dao
interface UserSelfPatternDao {

    @Query("""
        SELECT * FROM user_self_patterns
        WHERE personName = :personName
          AND senderBank = :senderBank
          AND direction = :direction
        LIMIT 1
    """)
    fun findPattern(
        personName: String,
        senderBank: String,
        direction: String
    ): UserSelfPattern?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(pattern: UserSelfPattern)

    @Query("SELECT * FROM user_self_patterns")
    fun getAll(): List<UserSelfPattern>

    @Query("DELETE FROM user_self_patterns WHERE id = :id")
    fun delete(id: Long)
}
