package com.utkusuer.hatirlatici.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PaymentWithReminders(
    @Embedded val payment: PaymentEntity,
    @Relation(parentColumn = "id", entityColumn = "paymentId")
    val reminderTimes: List<ReminderTimeEntity>,
)

data class CycleWithPayment(
    @Embedded val cycle: PaymentCycleEntity,
    @Relation(parentColumn = "paymentId", entityColumn = "id")
    val payment: PaymentEntity,
)
