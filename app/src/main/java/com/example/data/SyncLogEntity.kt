package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.SyncStatus

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String,
    val rawMessage: String,
    val method: String,
    val trxId: String,
    val amount: Double,
    val senderPhone: String?,
    val reference: String?,
    val balance: Double?,
    val status: String, // SUCCESS, FAILED, PARSED_ONLY, FILTERED_IGNORED
    val responseCode: Int,
    val responseBody: String,
    val endpointUrl: String,
    val errorMessage: String? = null
)
