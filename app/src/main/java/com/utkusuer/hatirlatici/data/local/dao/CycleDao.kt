package com.utkusuer.hatirlatici.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.utkusuer.hatirlatici.data.local.entity.CycleWithPayment
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {

    /** Aynı (paymentId, periodKey) zaten varsa dokunulmaz — "ödendi" bilgisi korunur. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(cycle: PaymentCycleEntity): Long

    @Query("SELECT * FROM payment_cycles WHERE id = :id")
    suspend fun getById(id: Long): PaymentCycleEntity?

    /** Bildirim zamanı gelmiş, ödenmemiş dönemler (gecikmişler dahil). */
    @Query(
        """SELECT * FROM payment_cycles
           WHERE paymentId = :paymentId AND status = 'PENDING' AND notifyFromEpochDay <= :today
           ORDER BY windowEndEpochDay"""
    )
    suspend fun getDueForPayment(paymentId: Long, today: Long): List<PaymentCycleEntity>

    @Query(
        """SELECT * FROM payment_cycles
           WHERE paymentId = :paymentId AND status = 'PENDING'
           ORDER BY notifyFromEpochDay"""
    )
    suspend fun getPendingForPayment(paymentId: Long): List<PaymentCycleEntity>

    @Query(
        """UPDATE payment_cycles SET status = 'PAID', paidAt = :paidAt, paidAmountMinor = :amountMinor
           WHERE id = :id"""
    )
    suspend fun markPaid(id: Long, paidAt: Long, amountMinor: Long?)

    @Query("UPDATE payment_cycles SET status = 'PENDING', paidAt = NULL, paidAmountMinor = NULL WHERE id = :id")
    suspend fun markUnpaid(id: Long)

    /** Zamanlama değiştiğinde, henüz ödenmemiş dönemler yeniden hesaplanmak üzere silinir. */
    @Query("DELETE FROM payment_cycles WHERE paymentId = :paymentId AND status = 'PENDING'")
    suspend fun deletePendingForPayment(paymentId: Long)

    /** Ana ekran: aktif kayıtların ödenmemiş ve [until] gününe kadar başlayan dönemleri. */
    @Transaction
    @Query(
        """SELECT c.* FROM payment_cycles c
           INNER JOIN payments p ON p.id = c.paymentId
           WHERE p.isActive = 1 AND c.status = 'PENDING' AND c.windowStartEpochDay <= :until
           ORDER BY c.windowEndEpochDay"""
    )
    fun observeOpen(until: Long): Flow<List<CycleWithPayment>>

    /** Widget: [observeOpen] ile aynı sorgu, tek seferlik. */
    @Transaction
    @Query(
        """SELECT c.* FROM payment_cycles c
           INNER JOIN payments p ON p.id = c.paymentId
           WHERE p.isActive = 1 AND c.status = 'PENDING' AND c.windowStartEpochDay <= :until
           ORDER BY c.windowEndEpochDay"""
    )
    suspend fun getOpen(until: Long): List<CycleWithPayment>

    /** Takvim: verilen gün aralığıyla kesişen tüm dönemler. */
    @Transaction
    @Query(
        """SELECT * FROM payment_cycles
           WHERE windowStartEpochDay <= :to AND windowEndEpochDay >= :from
           ORDER BY windowStartEpochDay"""
    )
    fun observeInRange(from: Long, to: Long): Flow<List<CycleWithPayment>>
}
