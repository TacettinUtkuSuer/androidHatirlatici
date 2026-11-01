package com.utkusuer.hatirlatici.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: ActivityLogEntity)

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 1000): Flow<List<ActivityLogEntity>>

    @Query("DELETE FROM activity_log")
    suspend fun clear()

    @Query("SELECT * FROM activity_log")
    suspend fun getAll(): List<ActivityLogEntity>

    @Insert
    suspend fun insertAll(entries: List<ActivityLogEntity>)
}
