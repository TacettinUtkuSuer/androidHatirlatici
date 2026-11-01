package com.utkusuer.hatirlatici.data.repository

import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.LogDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.CycleWithPayment
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentWithReminders
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.data.security.PinCipher
import com.utkusuer.hatirlatici.work.ReminderSync
import kotlinx.coroutines.flow.Flow

/**
 * UI'ın tek giriş noktası. Her değişiklik loglanır ve bildirim motoru yeniden senkronize edilir.
 */
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val cycleDao: CycleDao,
    private val logDao: LogDao,
    private val pinCipher: PinCipher,
    private val sync: ReminderSync,
) {
    fun observePayments(): Flow<List<PaymentWithReminders>> = paymentDao.observeAll()
    fun observePayment(id: Long): Flow<PaymentWithReminders?> = paymentDao.observeWithReminders(id)
    fun observeOpenCycles(untilEpochDay: Long): Flow<List<CycleWithPayment>> = cycleDao.observeOpen(untilEpochDay)
    fun observeCycles(fromEpochDay: Long, toEpochDay: Long): Flow<List<CycleWithPayment>> =
        cycleDao.observeInRange(fromEpochDay, toEpochDay)
    fun observeLog(): Flow<List<ActivityLogEntity>> = logDao.observeRecent()

    /**
     * Ekler veya günceller.
     * @param secrets null = hassas bilgilere dokunma; boş = sil; dolu = şifreleyip kaydet.
     */
    suspend fun save(payment: PaymentEntity, reminderMinutes: List<Int>, secrets: CardSecrets? = null): Long {
        var toSave = payment.copy(updatedAt = System.currentTimeMillis())
        if (secrets != null) {
            toSave = toSave.copy(
                encryptedSecrets = if (secrets.isEmpty) null else pinCipher.encrypt(secrets.toJson()),
                encryptedPin = null, // v1 PIN'i artık paketin içinde
                cardLast4 = secrets.last4 ?: toSave.cardLast4,
            )
        }
        val isNew = toSave.id == 0L
        val id = if (isNew) {
            paymentDao.insert(toSave)
        } else {
            paymentDao.update(toSave)
            // Zamanlama değişmiş olabilir: ödenmemiş dönemler yeniden hesaplansın.
            cycleDao.deletePendingForPayment(toSave.id)
            toSave.id
        }
        paymentDao.replaceReminderTimes(id, reminderMinutes)
        log(if (isNew) LogType.CREATED else LogType.UPDATED, id, toSave.title)
        sync.refreshNow()
        return id
    }

    suspend fun setActive(id: Long, active: Boolean) {
        paymentDao.setActive(id, active)
        paymentDao.getWithReminders(id)?.let {
            log(if (active) LogType.ACTIVATED else LogType.DEACTIVATED, id, it.payment.title)
        }
        sync.refreshNow()
    }

    suspend fun delete(payment: PaymentEntity) {
        paymentDao.delete(payment)
        log(LogType.DELETED, payment.id, payment.title)
        sync.refreshNow()
    }

    /** Hassas bilgileri çözer. Çağıran taraf önce kimlik doğrulaması yapmalıdır. */
    fun revealSecrets(payment: PaymentEntity): CardSecrets {
        val bundle = payment.encryptedSecrets?.let { CardSecrets.fromJson(pinCipher.decrypt(it)) } ?: CardSecrets()
        // v1'den kalan ayrı PIN
        val legacyPin = payment.encryptedPin?.let(pinCipher::decrypt)
        return if (bundle.pin.isBlank() && legacyPin != null) bundle.copy(pin = legacyPin) else bundle
    }

    suspend fun markUnpaid(cycleId: Long) {
        val cycle = cycleDao.getById(cycleId) ?: return
        cycleDao.markUnpaid(cycleId)
        paymentDao.getWithReminders(cycle.paymentId)?.let {
            log(LogType.UNPAID, cycle.paymentId, it.payment.title, "${cycle.periodKey} dönemi geri alındı")
        }
        sync.refreshNow()
    }

    suspend fun clearLog() = logDao.clear()

    private suspend fun log(type: LogType, paymentId: Long, title: String, detail: String? = null) {
        logDao.insert(ActivityLogEntity(type = type, paymentId = paymentId, title = title, detail = detail))
    }
}
