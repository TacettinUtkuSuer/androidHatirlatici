package com.utkusuer.hatirlatici.data.backup

import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.CycleStatus
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.ReminderTimeEntity
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.local.entity.WeekendRule
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val payment = PaymentEntity(
        id = 7, title = "Kart", bankCode = "akbank", kind = EntryKind.EXPENSE,
        scheduleType = ScheduleType.DATE_RANGE, dayStart = 1, dayEnd = 15, reminderDelayDays = 5,
        weekendRule = WeekendRule.NEAREST, startDateEpochDay = 20000, amountMinor = null,
        encryptedSecrets = "v1:device-bound", createdAt = 1, updatedAt = 2,
    )
    private val salary = PaymentEntity(
        id = 8, title = "Maaş", kind = EntryKind.INCOME, scheduleType = ScheduleType.SINGLE_DAY,
        dayStart = 3, startDateEpochDay = 20000, amountMinor = 5_000_000, createdAt = 1, updatedAt = 2,
    )
    private val data = BackupData(
        createdAt = 123,
        settings = AppSettings(defaultReminderMinutes = listOf(540, 1200), upcomingWindowDays = 10),
        payments = listOf(payment, salary),
        reminderTimes = listOf(ReminderTimeEntity(7, 600)),
        cycles = listOf(PaymentCycleEntity(1, 7, "2026-11", 1, 2, 1, CycleStatus.PAID, 99, null)),
        log = listOf(ActivityLogEntity(1, 5, LogType.PAID, 7, "Kart", "detay", 100)),
        secrets = mapOf(7L to CardSecrets(cardNumber = "4111111111111111", cvv = "123", pin = "4321")),
    )

    @Test fun roundTripWithPassword() {
        val json = BackupCodec.encode(data, "gizli-parola")
        assertFalse("düz kart no yedekte olmamalı", json.contains("4111111111111111"))
        assertFalse("cihaz anahtarlı şifre yedekte olmamalı", json.contains("device-bound"))
        val back = BackupCodec.decode(json, "gizli-parola")
        assertEquals(data.payments.map { it.copy(encryptedSecrets = null) }, back.payments)
        assertEquals(data.reminderTimes, back.reminderTimes)
        assertEquals(data.cycles, back.cycles)
        assertEquals(data.log, back.log)
        assertEquals(data.settings, back.settings)
        assertEquals(data.secrets, back.secrets)
    }

    @Test fun infoWithoutPassword() {
        val info = BackupCodec.info(BackupCodec.encode(data, "parola123"))
        assertEquals(2, info.paymentCount)
        assertTrue(info.hasSecrets)
    }

    @Test fun decodeWithoutPasswordSkipsSecrets() {
        val back = BackupCodec.decode(BackupCodec.encode(data, "parola123"), null)
        assertTrue(back.secrets.isEmpty())
        assertEquals(2, back.payments.size)
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPassword() {
        BackupCodec.decode(BackupCodec.encode(data, "dogru-parola"), "yanlis-parola")
    }

    @Test fun noSecretsNoPassword() {
        val json = BackupCodec.encode(data.copy(secrets = emptyMap()), null)
        assertFalse(BackupCodec.info(json).hasSecrets)
    }

    @Test(expected = InvalidBackupException::class)
    fun rejectsForeignJson() {
        BackupCodec.info("""{"foo":1}""")
    }
}
