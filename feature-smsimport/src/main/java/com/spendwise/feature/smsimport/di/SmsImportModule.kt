package com.spendwise.feature.smsimport.di

import android.content.Context
import com.spendwise.feature.smsimport.prefs.SmsImportPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsImportModule {

    @Provides
    @Singleton
    fun provideSmsImportPrefs(@ApplicationContext context: Context): SmsImportPrefs {
        return SmsImportPrefs(context)
    }
}
