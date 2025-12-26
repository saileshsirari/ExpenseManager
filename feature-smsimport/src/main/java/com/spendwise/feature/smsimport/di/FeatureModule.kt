package com.spendwise.feature.smsimport.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendwise.feature.smsimport.data.AppDatabase
import com.spendwise.feature.smsimport.data.SmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureModule {

    @Provides
    @Singleton
    fun provideFeatureDb(
        @ApplicationContext appContext: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "spendwise-feature.db"
        )
            // Apply all migrations
            .build()
    }

    // ❗ REQUIRED: Provide SmsDao so Hilt can inject it everywhere
    @Provides
    @Singleton
    fun provideSmsDao(db: AppDatabase): SmsDao {
        return db.smsDao()
    }
}

// ----------------------------------------------------------------------
// MIGRATIONS
// ----------------------------------------------------------------------

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new column with default = 0 (false)
        db.execSQL("ALTER TABLE sms ADD COLUMN isIgnored INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms ADD COLUMN linkId TEXT")
        db.execSQL("ALTER TABLE sms ADD COLUMN linkType TEXT")
        db.execSQL("ALTER TABLE sms ADD COLUMN linkConfidence INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sms ADD COLUMN isNetZero INTEGER NOT NULL DEFAULT 0")
    }
}


