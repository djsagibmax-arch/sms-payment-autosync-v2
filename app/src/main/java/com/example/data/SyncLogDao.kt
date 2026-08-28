package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 5")
    fun getRecentLogs(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs WHERE id = :id")
    suspend fun getLogById(id: Long): SyncLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity): Long

    @Update
    suspend fun updateLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM sync_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("SELECT COUNT(*) FROM sync_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_logs WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_logs WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM sync_logs WHERE status = 'SUCCESS'")
    fun getTotalSyncedAmount(): Flow<Double>
}
