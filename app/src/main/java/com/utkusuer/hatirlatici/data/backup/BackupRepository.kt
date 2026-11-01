package com.utkusuer.hatirlatici.data.backup

import androidx.room.withTransaction
import com.utkusuer.hatirlatici.data.local.AppDatabase
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.data.security.PinCipher
import com.utkusuer.hatirlatici.data.settings.SettingsRepository
import com.utkusuer.hatirlatici.work.ReminderSync

/**
 * Veritabanı ⇄ yedek JSON. Hassas bilgiler yedeğe yalnızca kullanıcı parolasıyla şifreli girer;
 * geri yüklemede bu cihazın Keystore anahtarıyla yeniden şifrelenir.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val cipher: PinCipher,
    private val sync: ReminderSync,
) {
    /** @param password null ise hassas bilgiler yedeğe dahil edilmez. */
    suspend fun export(password: String?): String {
        val payments = db.paymentDao().getAll()
        val secrets: Map<Long, CardSecrets> = if (password == null) emptyMap() else payments.mapNotNull { p ->
            val bundle = p.encryptedSecrets?.let { runCatching { CardSecrets.fromJson(cipher.decrypt(it)) }.getOrNull() } ?: CardSecrets()
            val legacyPin = p.encryptedPin?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
            val merged = if (bundle.pin.isBlank() && legacyPin != null) bundle.copy(pin = legacyPin) else bundle
            if (merged.isEmpty) null else p.id to merged
        }.toMap()
        val data = BackupData(
            createdAt = System.currentTimeMillis(),
            settings = settings.current(),
            payments = payments,
            reminderTimes = db.paymentDao().getAllReminderTimes(),
            cycles = db.cycleDao().getAll(),
            log = db.logDao().getAll(),
            secrets = secrets,
        )
        return BackupCodec.encode(data, password)
    }

    fun info(json: String): BackupInfo = BackupCodec.info(json)

    /** Mevcut tüm verileri siler ve yedeği yükler. Parola yanlışsa [WrongPasswordException]. */
    suspend fun import(json: String, password: String?) {
        val data = BackupCodec.decode(json, password)
        val payments = data.payments.map { p ->
            val s = data.secrets[p.id]
            p.copy(
                encryptedSecrets = s?.takeUnless { it.isEmpty }?.let { cipher.encrypt(it.toJson()) },
                encryptedPin = null,
            )
        }
        db.withTransaction {
            db.clearAllTables()
            db.paymentDao().insertAll(payments)
            db.paymentDao().insertReminderTimes(data.reminderTimes)
            db.cycleDao().insertAll(data.cycles)
            db.logDao().insertAll(data.log.map { it.copy(id = 0) })
            db.logDao().insert(
                ActivityLogEntity(
                    type = LogType.UPDATED,
                    title = "Yedekten geri yüklendi",
                    detail = "${payments.size} kayıt" + if (data.secrets.isNotEmpty()) " · kart bilgileriyle" else "",
                ),
            )
        }
        settings.replaceAll(data.settings)
        sync.refreshNow()
    }
}
