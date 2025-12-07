package com.spendwise.feature.smsimport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserMlOverrideDao {

    @Query("SELECT value FROM UserMlOverride WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(override: UserMlOverride)
}
