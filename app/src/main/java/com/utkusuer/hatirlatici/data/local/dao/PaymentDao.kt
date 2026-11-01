package com.utkusuer.hatirlatici.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.local.entity.ReminderTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Transaction
    @Query("SELECT * FROM payments ORDER BY isActive DESC, title COLLATE NOCASE")
    fun observeAll(): Flow<List<PaymentWithReminders>>

    @Transaction
    @Query("SELECT * FROM payments")
    suspend fun getAllWithReminders(): List<PaymentWithReminders>

    @Transaction
    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getWithReminders(id: Long): PaymentWithReminders?

    @Transaction
    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeWithReminders(id: Long): Flow<PaymentWithReminders?>

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("UPDATE payments SET isActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminder_times WHERE paymentId = :paymentId")
    suspend fun clearReminderTimes(paymentId: Long)

    @Insert
    suspend fun insertReminderTimes(times: List<ReminderTimeEntity>)

    @Transaction
    suspend fun replaceReminderTimes(paymentId: Long, minutes: List<Int>) {
        clearReminderTimes(paymentId)
        if (minutes.isNotEmpty()) {
            insertReminderTimes(minutes.distinct().sorted().map { ReminderTimeEntity(paymentId, it) })
        }
    }
}
