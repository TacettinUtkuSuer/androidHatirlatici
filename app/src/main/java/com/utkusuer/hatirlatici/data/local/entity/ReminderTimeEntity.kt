package com.utkusuer.hatirlatici.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Bir ödemeye özel gün içi hatırlatma saati (ör. 600 = 10:00). */
@Entity(
    tableName = "reminder_times",
    primaryKeys = ["paymentId", "minuteOfDay"],
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paymentId")],
)
data class ReminderTimeEntity(
    val paymentId: Long,
    val minuteOfDay: Int,
)
