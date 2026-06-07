package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconChar: String,
    val description: String
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val onlineCount: Int,
    val latencyCategory: String // "Low", "Ultra Low", "High Fidelity"
)

@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val serverName: String,
    val channelName: String,
    val latencyMs: Int,
    val packetLossPercent: Double,
    val codecBitrateKbps: Int,
    val performanceMode: String,
    val deviceStatus: String // "Standard", "ECO Mode"
)
