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
        // If empty, OR if it contains the old generic servers, clear and repopulate them!
        val needsRefresh = currentServers.isEmpty() || currentServers.any { it.name == "Gaming Lounges" || it.name == "Developer Forge" }
        if (needsRefresh) {
            serverDao.clearServers()
            channelDao.clearChannels()

            // Prepopulate beautiful Anime themed servers
            val s1 = ServerEntity(name = "Uchiha Sanctuary", iconChar = "🦅", description = "Sasuke & Itachi Brotherhood voice lounge.")
            val s2 = ServerEntity(name = "Konoha Leaf Village", iconChar = "🍥", description = "Naruto Uzumaki Seventh Hokage tactical squad.")
            val s3 = ServerEntity(name = "Akatsuki Fortress", iconChar = "☁️", description = "Shinobi syndicate high-bandwidth operation center.")
            val s4 = ServerEntity(name = "Madara's Infinite Realm", iconChar = "👁️", description = "Low latency dreamworld sound domain.")

            val s1Id = serverDao.insertServer(s1)
            val s2Id = serverDao.insertServer(s2)
            val s3Id = serverDao.insertServer(s3)
            val s4Id = serverDao.insertServer(s4)

            // Prepopulate customized voice channels corresponding to the areas
            val channels = listOf(
                // Uchiha Sanctuary (Sasuke & Itachi)
                ChannelEntity(serverId = s1Id, name = "Susanoo Training Ground ⚔️", onlineCount = 3, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s1Id, name = "Amaterasu Fire Chamber 🔥", onlineCount = 1, latencyCategory = "Low"),
                ChannelEntity(serverId = s1Id, name = "Tsukuyomi Mental Lounge ⏳", onlineCount = 2, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s1Id, name = "Sharingan Focal Room 👁️‍🗨️", onlineCount = 0, latencyCategory = "Standard"),

                // Konoha Leaf Village (Naruto)
                ChannelEntity(serverId = s2Id, name = "Hokage War Room 🍥", onlineCount = 4, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s2Id, name = "Ramen Ichiraku Chill 🍜", onlineCount = 5, latencyCategory = "Low"),
                ChannelEntity(serverId = s2Id, name = "Shadow Clone Practice 👥", onlineCount = 2, latencyCategory = "Standard"),
                ChannelEntity(serverId = s2Id, name = "Great Stone Faces Summit 🗻", onlineCount = 0, latencyCategory = "Standard"),

                // Akatsuki Fortress (Itachi Clan)
                ChannelEntity(serverId = s3Id, name = "Reaper Death Seal Room 💀", onlineCount = 3, latencyCategory = "Low"),
                ChannelEntity(serverId = s3Id, name = "C3 Clay Explosion Chamber 💥", onlineCount = 1, latencyCategory = "Standard"),
                ChannelEntity(serverId = s3Id, name = "Syndicate Operations Hub 🌌", onlineCount = 6, latencyCategory = "Ultra Low"),

                // Madara's Infinite Realm (Madara)
                ChannelEntity(serverId = s4Id, name = "Infinite Tsukuyomi Echo 👁️", onlineCount = 4, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s4Id, name = "Valley of the End Arena 🤜", onlineCount = 2, latencyCategory = "Ultra Low"),
                ChannelEntity(serverId = s4Id, name = "Gedo Statue Core 🗿", onlineCount = 1, latencyCategory = "Low")
            )
            channelDao.insertChannels(channels)
        }
    }
}
