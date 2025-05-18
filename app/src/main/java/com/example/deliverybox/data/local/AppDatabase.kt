package com.example.deliverybox.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.deliverybox.data.local.dao.BoxDao
import com.example.deliverybox.data.local.dao.PackageDao
import com.example.deliverybox.data.local.dao.UserBoxDao
import com.example.deliverybox.data.local.entity.BoxEntity
import com.example.deliverybox.data.local.entity.DeliveryStepEntity
import com.example.deliverybox.data.local.entity.PackageEntity
import com.example.deliverybox.data.local.entity.UserBoxEntity

@Database(
    entities = [
        PackageEntity::class,
        DeliveryStepEntity::class,
        BoxEntity::class,
        UserBoxEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packageDao(): PackageDao
    abstract fun boxDao(): BoxDao
    abstract fun userBoxDao(): UserBoxDao
}
