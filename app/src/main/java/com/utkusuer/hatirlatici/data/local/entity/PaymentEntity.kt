package com.utkusuer.hatirlatici.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bir ödeme tanımı (kart, fatura, kredi...). Aylık dönemler [PaymentCycleEntity] olarak üretilir.
 *
 * Tarih alanları `LocalDate.toEpochDay()` olarak saklanır.
 */
@Entity(tableName = "payments", indices = [Index("isActive")])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val institution: String? = null,
    val category: PaymentCategory = PaymentCategory.OTHER,

    // --- Zamanlama ---
    val scheduleType: ScheduleType,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    /** SINGLE_DAY: son ödeme günü. DATE_RANGE: aralığın ilk günü. (1..31, kısa aylarda ay sonuna kırpılır) */
    val dayStart: Int,
    /** DATE_RANGE: aralığın son günü. dayStart'tan küçükse bir sonraki aya taşar (ör. 25 → 10). */
    val dayEnd: Int? = null,
    /** DATE_RANGE: "İlk X gün bana hatırlatma" (offset). */
    val reminderDelayDays: Int = 0,
    /** SINGLE_DAY: vadeden X gün önce hatırlatmaya başla. */
    val leadDays: Int = 0,
    /** İlk dönemin hesaplanacağı tarih. */
    val startDateEpochDay: Long,
    /** Bu tarihten sonra dönem üretilmez (opsiyonel). */
    val endDateEpochDay: Long? = null,
    /** Taksitli ödemelerde toplam taksit sayısı (opsiyonel). */
    val totalInstallments: Int? = null,

    // --- Tutar ---
    /** Kuruş cinsinden tutar. Null = tutar her ay değişiyor / girilmedi. */
    val amountMinor: Long? = null,
    val currency: String = "TRY",

    // --- Durum & bildirim ---
    /** Pasif kayıtlar saklanır ama hiçbir bildirim üretmez. */
    val isActive: Boolean = true,
    val persistentNotification: Boolean = true,
    /** true: Ayarlar'daki genel saatler kullanılır; false: [ReminderTimeEntity] kayıtları. */
    val useDefaultReminderTimes: Boolean = true,

    // --- Kart bilgisi ---
    val cardLast4: String? = null,
    /** Android Keystore (AES-256/GCM) ile şifrelenmiş PIN; asla düz metin değil. */
    val encryptedPin: String? = null,

    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
