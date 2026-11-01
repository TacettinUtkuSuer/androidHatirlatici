package com.utkusuer.hatirlatici.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.ReminderTimeEntity

@Database(
    entities = [PaymentEntity::class, ReminderTimeEntity::class, PaymentCycleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun cycleDao(): CycleDao

    companion object {
        const val NAME = "hatirlatici.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
    }
}
