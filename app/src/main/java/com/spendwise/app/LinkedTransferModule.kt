package com.spendwise.app

import com.spendwise.core.linked.LinkedTransactionDetector
import com.spendwise.core.linked.LinkedTransactionRepository
import com.spendwise.domain.com.spendwise.feature.smsimport.repo.LinkedTransactionRepositoryImpl
import com.spendwise.feature.smsimport.data.SmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LinkedTransferModule {

    @Provides
    @Singleton
    fun provideLinkedTransactionRepository(dao: SmsDao): LinkedTransactionRepository {
        return LinkedTransactionRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideLinkedTransactionDetector(repo: LinkedTransactionRepository): LinkedTransactionDetector {
        return LinkedTransactionDetector(repo)
    }
}
