package com.example.deliverybox.di

import android.content.Context
import com.example.deliverybox.delivery.PackageNotificationManager
import com.example.deliverybox.delivery.api.DeliveryTrackerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideDeliveryTrackerService(): DeliveryTrackerService {
        return DeliveryTrackerService(
            apiKey = "" // TODO: BuildConfig.DELIVERY_TRACKER_API_KEY로 교체
        )
    }

    @Provides
    @Singleton
    fun providePackageNotificationManager(
        @ApplicationContext context: Context
    ): PackageNotificationManager {
        return PackageNotificationManager(context)
    }
}