package com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserMlOverrideDao {

    @Query("SELECT value FROM user_ml_override WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: UserMlOverride)

    @Query("SELECT value FROM user_ml_override WHERE `key` LIKE 'ignore_pattern:%'")
    suspend fun getIgnorePatterns(): List<String>
}

