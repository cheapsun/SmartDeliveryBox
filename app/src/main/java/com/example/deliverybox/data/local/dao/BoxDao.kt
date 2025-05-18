package com.example.deliverybox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.deliverybox.data.local.entity.BoxEntity

@Dao
interface BoxDao {

    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun getBoxById(id: String): BoxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBox(box: BoxEntity)
}
