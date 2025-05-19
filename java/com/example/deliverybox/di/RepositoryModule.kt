package com.example.deliverybox.di

import android.content.Context
import androidx.room.Room
import com.example.deliverybox.data.local.AppDatabase
import com.example.deliverybox.data.local.dao.PackageDao
import com.example.deliverybox.data.repositories.PackageRepositoryImpl
import com.example.deliverybox.domain.repositories.PackageRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPackageRepository(
        impl: PackageRepositoryImpl
    ): PackageRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "delivery_box_database"
        ).build()
    }

    @Provides
    fun providePackageDao(database: AppDatabase): PackageDao = database.packageDao()
}