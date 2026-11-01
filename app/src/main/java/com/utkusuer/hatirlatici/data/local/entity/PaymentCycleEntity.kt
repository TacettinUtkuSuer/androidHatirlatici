package com.utkusuer.hatirlatici.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bir ödemenin belirli bir aydaki dönemi. "Ödendi" bilgisi burada tutulur;
 * böylece geçmiş aylar takvimde görülebilir ve ödenmeyen dönemler bildirim üretmeye devam eder.
 */
@Entity(
    tableName = "payment_cycles",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["paymentId", "periodKey"], unique = true),
        Index("status"),
        Index("windowEndEpochDay"),
    ],
)
data class PaymentCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    /** "yyyy-MM" — dönemin ait olduğu ay. */
    val periodKey: String,
    val windowStartEpochDay: Long,
    val windowEndEpochDay: Long,
    /** Bildirimlerin başlayacağı gün (offset / lead uygulanmış). */
    val notifyFromEpochDay: Long,
    val status: CycleStatus = CycleStatus.PENDING,
    val paidAt: Long? = null,
    val paidAmountMinor: Long? = null,
)
