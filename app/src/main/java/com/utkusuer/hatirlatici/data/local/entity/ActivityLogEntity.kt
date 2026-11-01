package com.utkusuer.hatirlatici.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Değiştirilemez işlem günlüğü. Kayıt silinse bile log kalsın diye paymentId yabancı anahtar değildir
 * ve başlık o anki haliyle kopyalanır.
 */
@Entity(tableName = "activity_log", indices = [Index("timestamp")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val paymentId: Long? = null,
    val title: String,
    val detail: String? = null,
    val amountMinor: Long? = null,
)
