package com.example.data

import com.example.data.ServerEntity
import com.example.data.ChannelEntity
import com.example.data.ConnectionLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoiceRepository(private val database: AppDatabase) {
    private val serverDao = database.serverDao()
    private val channelDao = database.channelDao()
    private val connectionLogDao = database.connectionLogDao()

    fun getAllServers(): Flow<List<ServerEntity>> = serverDao.getAllServers()

    fun getChannelsByServer(serverId: Long): Flow<List<ChannelEntity>> = 
        channelDao.getChannelsByServer(serverId)

    fun getRecentLogs(): Flow<List<ConnectionLogEntity>> = 
        connectionLogDao.getRecentLogs()

    suspend fun insertLog(log: ConnectionLogEntity) = withContext(Dispatchers.IO) {
        connectionLogDao.insertLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        connectionLogDao.clearLogs()
    }

    suspend fun checkAndPrepopulate() = withContext(Dispatchers.IO) {
        val currentServers = serverDao.getAllServers().first()
        if (currentServers.isEmpty()) {
            // Prepopulate Servers
            val s1 = ServerEntity(name = "Gaming Lounges", iconChar = "🎮", description = "Low Latency squad voice channels.")
            val s2 = ServerEntity(name = "Developer Forge", iconChar = "💻", description = "High-performance programming co-working.")
            val s3 = ServerEntity(name = "Music & Vibes", iconChar = "🎵", description = "Lo-Fi audio streaming and casual chats.")
            val s4 = ServerEntity(name = "NAW TALKING HQ", iconChar = "⚡", description = "Lite-weight channel testbeds for older phones.")

            val s1Id = serverDao.insertServer(s1)
            val s2Id = serverDao.insertServer(s2)
            val s3Id = serverDao.insertServer(s3)
            val s4Id = serverDao.insertServer(s4)

            // Prepopulate Channels
            val channels = listOf(
                // Gaming
                ChannelEntity(serverId = s1Id, name = "Squad Lobby 🎤", onlineCount = 3, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s1Id, name = "Duo Tacticians 🎯", onlineCount = 1, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s1Id, name = "Competitive Arena 🏆", onlineCount = 5, latencyCategory = "Low"),
                ChannelEntity(serverId = s1Id, name = "Retro Chill 🕹️", onlineCount = 0, latencyCategory = "Standard"),

                // Dev Forge
                ChannelEntity(serverId = s2Id, name = "Daily Standup Meeting 📋", onlineCount = 4, latencyCategory = "Low"),
                ChannelEntity(serverId = s2Id, name = "Pair Programming Code-A", onlineCount = 2, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s2Id, name = "DevOps Incidents Room 🚨", onlineCount = 0, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s2Id, name = "Geeks Corner 🤓", onlineCount = 1, latencyCategory = "Standard"),

                // Music & Vibes
                ChannelEntity(serverId = s3Id, name = "Lo-Fi Beats Chill 🎧", onlineCount = 8, latencyCategory = "High Fidelity"),
                ChannelEntity(serverId = s3Id, name = "Acoustic Lounge 🎸", onlineCount = 2, latencyCategory = "High Fidelity"),
                ChannelEntity(serverId = s3Id, name = "Humming & Whistling 😗", onlineCount = 0, latencyCategory = "Standard"),

                // NAW TALKING HQ
                ChannelEntity(serverId = s4Id, name = "8kHz Eco Compression 🔋", onlineCount = 1, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s4Id, name = "16kHz Low Latency Radio 📻", onlineCount = 2, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s4Id, name = "Dev Loopback Test Server 🛠️", onlineCount = 0, latencyCategory = "Ultra Low")
            )
            channelDao.insertChannels(channels)
        }
    }
}
