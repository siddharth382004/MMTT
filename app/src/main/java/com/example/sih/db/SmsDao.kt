package com.example.sih.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(sms: SmsEntity)

    @Query("SELECT * FROM sms_table ORDER BY id DESC")
    fun getAllMessages(): List<SmsEntity>

    @Delete
    fun deleteMessages(messages: List<SmsEntity>)
}
