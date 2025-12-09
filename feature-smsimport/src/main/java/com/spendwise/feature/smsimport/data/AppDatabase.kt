
package com.spendwise.feature.smsimport.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SmsEntity::class, UserMlOverride::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun userMlOverrideDao(): UserMlOverrideDao

}
