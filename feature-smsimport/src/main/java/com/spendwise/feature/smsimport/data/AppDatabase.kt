
package com.spendwise.feature.smsimport.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.spendwise.domain.com.spendwise.feature.smsimport.data.UserSelfPatternDao

@Database(entities = [SmsEntity::class, UserMlOverride::class, LinkedPatternEntity::class ,
    SmsDao.SelfRecipientEntity::class , UserSelfPattern::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun userMlOverrideDao(): UserMlOverrideDao

}
