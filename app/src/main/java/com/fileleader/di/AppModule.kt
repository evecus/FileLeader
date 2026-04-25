package com.fileleader.di

import android.content.Context
import androidx.room.Room
import com.fileleader.data.db.FileLeaderDatabase
import com.fileleader.data.db.ScanHistoryDao
import com.fileleader.data.db.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FileLeaderDatabase =
        Room.databaseBuilder(context, FileLeaderDatabase::class.java, "fileleader.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTrashDao(db: FileLeaderDatabase): TrashDao = db.trashDao()

    @Provides
    fun provideScanHistoryDao(db: FileLeaderDatabase): ScanHistoryDao = db.scanHistoryDao()
}
