package com.utkusuer.hatirlatici.data.repository

import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.CycleWithPayment
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.security.PinCipher
import com.utkusuer.hatirlatici.work.ReminderSync
import kotlinx.coroutines.flow.Flow

/**
 * UI'ın tek giriş noktası. Her değişiklikten sonra bildirim motoru yeniden senkronize edilir.
 */
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val cycleDao: CycleDao,
    private val pinCipher: PinCipher,
    private val sync: ReminderSync,
) {
    fun observePayments(): Flow<List<PaymentWithReminders>> = paymentDao.observeAll()
    fun observePayment(id: Long): Flow<PaymentWithReminders?> = paymentDao.observeWithReminders(id)
    fun observeOpenCycles(untilEpochDay: Long): Flow<List<CycleWithPayment>> = cycleDao.observeOpen(untilEpochDay)
    fun observeCycles(fromEpochDay: Long, toEpochDay: Long): Flow<List<CycleWithPayment>> =
        cycleDao.observeInRange(fromEpochDay, toEpochDay)

    /**
     * Ekler veya günceller.
     * @param newPin null = PIN'e dokunma, "" = PIN'i sil, diğer = şifreleyip kaydet.
     */
    suspend fun save(payment: PaymentEntity, reminderMinutes: List<Int>, newPin: String? = null): Long {
        val encryptedPin = when {
            newPin == null -> payment.encryptedPin
            newPin.isEmpty() -> null
            else -> pinCipher.encrypt(newPin)
        }
        val toSave = payment.copy(encryptedPin = encryptedPin, updatedAt = System.currentTimeMillis())
        val id = if (toSave.id == 0L) {
            paymentDao.insert(toSave)
        } else {
            paymentDao.update(toSave)
            // Zamanlama değişmiş olabilir: ödenmemiş dönemler yeniden hesaplansın.
            cycleDao.deletePendingForPayment(toSave.id)
            toSave.id
        }
        paymentDao.replaceReminderTimes(id, reminderMinutes)
        sync.refreshNow()
        return id
    }

    suspend fun setActive(id: Long, active: Boolean) {
        paymentDao.setActive(id, active)
        sync.refreshNow()
    }

    suspend fun delete(payment: PaymentEntity) {
        paymentDao.delete(payment)
        sync.refreshNow()
    }

    fun revealPin(payment: PaymentEntity): String? = payment.encryptedPin?.let(pinCipher::decrypt)

    suspend fun markUnpaid(cycleId: Long) {
        cycleDao.markUnpaid(cycleId)
        sync.refreshNow()
    }
}
