package com.dominar.ride.di

import android.content.Context
import androidx.room.Room
import com.dominar.ride.data.db.AppDatabase
import com.dominar.ride.data.db.ServiceLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dominar.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideServiceLogDao(db: AppDatabase): ServiceLogDao = db.serviceLogDao()
}
