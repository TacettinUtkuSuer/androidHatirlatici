package com.utkusuer.hatirlatici.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.utkusuer.hatirlatici.data.local.dao.CycleDao
import com.utkusuer.hatirlatici.data.local.dao.LogDao
import com.utkusuer.hatirlatici.data.local.dao.PaymentDao
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.ReminderTimeEntity

@Database(
    entities = [PaymentEntity::class, ReminderTimeEntity::class, PaymentCycleEntity::class, ActivityLogEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun cycleDao(): CycleDao
    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "hatirlatici.db"

        /** v1 → v2: gelir türü, hafta sonu kuralı, banka, şifreli kart bilgileri ve işlem günlüğü. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE payments ADD COLUMN bankCode TEXT")
                db.execSQL("ALTER TABLE payments ADD COLUMN weekendRule TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE payments ADD COLUMN encryptedSecrets TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS activity_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        paymentId INTEGER,
                        title TEXT NOT NULL,
                        detail TEXT,
                        amountMinor INTEGER
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_log_timestamp ON activity_log (timestamp)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
