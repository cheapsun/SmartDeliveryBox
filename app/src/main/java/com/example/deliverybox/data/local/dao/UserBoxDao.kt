package com.example.deliverybox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.deliverybox.data.local.entity.UserBoxEntity

@Dao
interface UserBoxDao {

    @Query("SELECT * FROM user_boxes WHERE userId = :userId")
    suspend fun getBoxesForUser(userId: String): List<UserBoxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserBox(userBox: UserBoxEntity)
}
