package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.AppDatabase
import com.example.data.ChannelEntity
import com.example.data.ConnectionLogEntity
import com.example.data.ServerEntity
import com.example.data.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelMember(
    val name: String,
    val avatarColor: Long, // Hex color
    val isSpeaking: Boolean,
    val deviceStatus: String, // e.g. "Low Perf", "Power Save", "Fidelity"
    val latencyMs: Int
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = VoiceRepository(database)
    private val audioEngine = AudioEngine(application)

    // Database Streams
    val servers: StateFlow<List<ServerEntity>> = repository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionLogs: StateFlow<List<ConnectionLogEntity>> = repository.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Guest Login States
    private val sharedPrefs = application.getSharedPreferences("naw_talking_prefs", Context.MODE_PRIVATE)
    val isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val guestUsername = MutableStateFlow(sharedPrefs.getString("guest_username", "") ?: "")
    val guestPassword = MutableStateFlow(sharedPrefs.getString("guest_password", "") ?: "")

    fun loginGuest(user: String, pass: String) {
        sharedPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("guest_username", user)
            .putString("guest_password", pass)
            .apply()
        isLoggedIn.value = true
        guestUsername.value = user
        guestPassword.value = pass
        
        // Refresh active members display name
        _connectedChannel.value?.let { channel ->
            spawnMockMembers(channel)
        }
    }

    fun logout() {
        sharedPrefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("guest_username")
            .remove("guest_password")
            .apply()
        isLoggedIn.value = false
        guestUsername.value = ""
        guestPassword.value = ""
        leaveVoiceChannel()
    }

    // UI Interactive States
    val selectedServer = MutableStateFlow<ServerEntity?>(null)
    
    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels: StateFlow<List<ChannelEntity>> = _channels

    private val _connectedChannel = MutableStateFlow<ChannelEntity?>(null)
    val connectedChannel: StateFlow<ChannelEntity?> = _connectedChannel

    private val _connectionStatus = MutableStateFlow("Disconnected") // "Disconnected", "Connecting...", "Connected"
    val connectionStatus: StateFlow<String> = _connectionStatus

    // Voice & Codec Controls
    val isMuted = MutableStateFlow(false)
    val isDeafened = MutableStateFlow(false)
    val isLoopbackEnabled = MutableStateFlow(false)
    val connectionLatency = MutableStateFlow(0) // dynamic ping simulator

    // Push-to-Talk (PTT)
    val isPushToTalkEnabled = MutableStateFlow(false)
    val isPushToTalkActive = MutableStateFlow(false)
    val pttTriggerKey = MutableStateFlow("Volume Down (Recommended)") // "Volume Down (Recommended)", "Volume Up"

    // Optimization Parameter Adjustments for Older Hardware
    val audioEngineSampleRate = MutableStateFlow(16000) // 8000, 16000, 44100, 48000
    val codecBitrate = MutableStateFlow(16) // 8, 16, 32, 64, 128 kbps
    val isEcoOptimizationActive = MutableStateFlow(false) // toggle eco graphics & animations
    val isGameBoosterEnabled = MutableStateFlow(false) // Real-time ultra low jitter booster for gaming
    val isNoiseSuppressionEnabledByUI = audioEngine.isNoiseSuppressionActive // Real-time noise suppressor and keyboard click filter
    val performanceMode = MutableStateFlow("Ultra Eco") // "Ultra Eco", "Balanced", "Fidelity", "Zero-Lag Game Mode"

    // Real-time battery diagnostic stats
    val localBatteryInfo = MutableStateFlow("Optimizing...")

    // Microphone Level State Flow
    val microphoneVolume = audioEngine.amplitude
    val nativeBufferInfo = audioEngine.nativeBufferInfo
    val threadProcessTimeUs = audioEngine.bufferUsageUs

    // Channel Member Simulations
    private val _activeMembers = MutableStateFlow<List<ChannelMember>>(emptyList())
    val activeMembers: StateFlow<List<ChannelMember>> = _activeMembers

    private var channelWatcherJob: Job? = null
    private var simulationSpeakerJob: Job? = null
    private var batteryStatusJob: Job? = null

    init {
        instance = this
        // Prepopulate db and set default server on first run
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            
            // Once populated, observe the first server and select it
            servers.collect { serverList ->
                if (serverList.isNotEmpty() && selectedServer.value == null) {
                    selectServer(serverList.first())
                }
            }
        }

        // Start battery monitoring routine
        startBatteryMonitoring()
    }

    /**
     * Change current discord-like server context.
     */
    fun selectServer(server: ServerEntity) {
        selectedServer.value = server
        viewModelScope.launch {
            repository.getChannelsByServer(server.id).collect { channelList ->
                _channels.value = channelList
            }
        }
    }

    /**
     * Start connecting to a physical/simulated low-latency voice channel.
     */
    fun joinVoiceChannel(channel: ChannelEntity) {
        // Safe disconnection of previous channel first
        leaveVoiceChannel()

        _connectionStatus.value = "Connecting..."
        _connectedChannel.value = channel

        viewModelScope.launch {
            // Simulate very low latency handshaking (<200ms)
            val handshakeMin = if (performanceMode.value == "Ultra Eco") 50 else 110
            val handshakeMax = if (performanceMode.value == "Ultra Eco") 120 else 190
            val mockPing = (handshakeMin..handshakeMax).random()
            
            delay(150) // connection overhead
            connectionLatency.value = mockPing
            _connectionStatus.value = "Connected"

            // Log connection data gracefully into the Room database
            logConnectEvent(channel, mockPing)

            // Start hardware audio recorder engine
            audioEngine.configure(
                rate = audioEngineSampleRate.value,
                ecoEnabled = isEcoOptimizationActive.value,
                boosterEnabled = isGameBoosterEnabled.value
            )
            audioEngine.start()
            applyAudioEngineMute()

            // Populate mock participants in the active channel to simulate live chats
            spawnMockMembers(channel)
        }
    }

    /**
     * Disconnects from the current channel.
     */
    fun leaveVoiceChannel() {
        val prevChannel = _connectedChannel.value
        if (prevChannel != null) {
            _connectedChannel.value = null
            _connectionStatus.value = "Disconnected"
            _activeMembers.value = emptyList()
            connectionLatency.value = 0
            audioEngine.stop()
            stopSimulationJobs()
            dismissNotification()
        }
    }

    /**
     * Applies the correct muting state to the audio engine based on the combination
     * of Deafen, manual Mute, and modern Push-to-Talk states.
     */
    private fun applyAudioEngineMute() {
        if (isDeafened.value) {
            audioEngine.setMute(true)
        } else if (isMuted.value) {
            audioEngine.setMute(true)
        } else if (isPushToTalkEnabled.value) {
            // In Push-to-Talk mode, mic is muted unless PTT is actively pressed down
            audioEngine.setMute(!isPushToTalkActive.value)
        } else {
            audioEngine.setMute(false)
        }
        if (connectedChannel.value != null) {
            showOrUpdateNotification()
        }
    }

    /**
     * Toggle microphone mute state.
     */
    fun toggleMute() {
        isMuted.value = !isMuted.value
        applyAudioEngineMute()
    }

    /**
     * Toggle global user audio playback hearing (Deafen).
     */
    fun toggleDeafen() {
        val current = isDeafened.value
        isDeafened.value = !current
        if (!current) {
            // Deafening also mutes microphone to prevent echo
            isMuted.value = true
        } else {
            isMuted.value = false
        }
        applyAudioEngineMute()
    }

    /**
     * Toggle local loopback monitoring speaker.
     */
    fun toggleLoopback() {
        val current = isLoopbackEnabled.value
        isLoopbackEnabled.value = !current
        audioEngine.setLoopback(!current)
    }

    /**
     * Toggles the Push-to-Talk communication mode.
     */
    fun togglePushToTalk() {
        val next = !isPushToTalkEnabled.value
        isPushToTalkEnabled.value = next
        // If enabling, reset active state to false
        if (next) {
            isPushToTalkActive.value = false
        }
        applyAudioEngineMute()
    }

    /**
     * Sets whether the user is actively pushing holding the talk button or key.
     */
    fun setPushToTalkActive(active: Boolean) {
        if (isPushToTalkActive.value != active) {
            isPushToTalkActive.value = active
            applyAudioEngineMute()
        }
    }

    /**
     * Toggles between Volume Down and Volume Up as PTT hardware trigger keys.
     */
    fun togglePTTTriggerKey() {
        if (pttTriggerKey.value.contains("Volume Down")) {
            pttTriggerKey.value = "Volume Up"
        } else {
            pttTriggerKey.value = "Volume Down (Recommended)"
        }
    }

    /**
     * Highlight gaming optimization and minimize latency jitter.
     */
    fun toggleGameBooster() {
        val nextState = !isGameBoosterEnabled.value
        isGameBoosterEnabled.value = nextState
        audioEngine.setGameBooster(nextState)
        
        // When game booster is enabled, update performanceMode descriptive label
        if (nextState) {
            performanceMode.value = "Zero-Lag Game Mode"
        } else {
            // Re-infer mode
            performanceMode.value = when {
                isEcoOptimizationActive.value && audioEngineSampleRate.value <= 16000 -> "Ultra Eco"
                !isEcoOptimizationActive.value && audioEngineSampleRate.value >= 44100 -> "Fidelity+"
                else -> "Balanced"
            }
        }
    }

    /**
     * Toggles acoustic noise suppression and transient keyboard click filter.
     */
    fun toggleNoiseSuppression() {
        val nextState = !isNoiseSuppressionEnabledByUI.value
        audioEngine.setNoiseSuppression(nextState)
    }

    /**
     * Change audio rate and codecs to demonstrate retro / high-performance tradeoffs.
     */
    fun changeAudioConfiguration(rate: Int, ecoEnabled: Boolean, bitrateKbps: Int) {
        audioEngineSampleRate.value = rate
        isEcoOptimizationActive.value = ecoEnabled
        codecBitrate.value = bitrateKbps

        performanceMode.value = when {
            isGameBoosterEnabled.value -> "Zero-Lag Game Mode"
            ecoEnabled && rate <= 16000 -> "Ultra Eco"
            !ecoEnabled && rate >= 44100 -> "Fidelity+"
            else -> "Balanced"
        }

        // Apply to direct running audio engine
        audioEngine.configure(
            rate = rate,
            ecoEnabled = ecoEnabled,
            boosterEnabled = isGameBoosterEnabled.value
        )
    }

    /**
     * Log joining event reactively to local Room audit.
     */
    private suspend fun logConnectEvent(channel: ChannelEntity, ping: Int) {
        val activeServerName = selectedServer.value?.name ?: "Unknown"
        val log = ConnectionLogEntity(
            serverName = activeServerName,
            channelName = channel.name,
            latencyMs = ping,
            packetLossPercent = if (performanceMode.value == "Ultra Eco") 0.05 else 0.02,
            codecBitrateKbps = codecBitrate.value,
            performanceMode = performanceMode.value,
            deviceStatus = if (isEcoOptimizationActive.value) "ECO Mode Enabled" else "Normal Graphic Level"
        )
        repository.insertLog(log)
    }

    suspend fun clearLogs() {
        repository.clearLogs()
    }

    /**
     * Spawns interesting other Discord members inside the channel.
     */
    private fun spawnMockMembers(channel: ChannelEntity) {
        stopSimulationJobs()

        // Spawn simulated members with high/low performance tags
        val currentGuest = guestUsername.value.ifBlank { "Riley" }
        val candidates = listOf(
            ChannelMember("Alex 🎮", 0xFFAF52BE, false, "Performance", 12),
            ChannelMember("Taylor 🦊", 0xFFE91E63, false, "Eco Core", 28),
            ChannelMember("Jordan 🎧", 0xFF009688, false, "Legacy", 45),
            ChannelMember("$currentGuest (You)", 0xFF2196F3, false, "My Device", 10)
        )

        // Select 2-3 members based on channel default counts
        val subCount = channel.onlineCount.coerceAtMost(candidates.size).coerceAtLeast(1)
        val selected = candidates.shuffled().take(subCount).toMutableList()

        // Always put "Riley (You)" inside
        if (selected.none { it.name.contains("(You)") }) {
            selected.removeAt(selected.size - 1)
            selected.add(candidates.last())
        }

        _activeMembers.value = selected

        // Simulate voice indicators shifting when speaking
        simulationSpeakerJob = viewModelScope.launch(Dispatchers.Default) {
            while (this.isActive) {
                _activeMembers.value = _activeMembers.value.map { member ->
                    if (member.name.contains("(You)")) {
                        // "You" speaking flag matches actual/simulated microphone volume threshold
                        member.copy(isSpeaking = microphoneVolume.value > 0.05f)
                    } else {
                        // Periodic simulated chatter for mock peers
                        val speakProbability = if (Math.random() > 0.70) !member.isSpeaking else member.isSpeaking
                        member.copy(isSpeaking = speakProbability)
                    }
                }
                delay(750)
            }
        }
    }

    /**
     * Background query of current tablet/phone hardware battery diagnostics.
     */
    private fun startBatteryMonitoring() {
        batteryStatusJob = viewModelScope.launch(Dispatchers.Default) {
            val context = getApplication<Application>().applicationContext
            while (this.isActive) {
                var info = "Temp: Normal | Save Mode"
                try {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (batteryIntent != null) {
                        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val temperatureInCelsius = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) // millivolts
                        
                        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                        val speedPrefix = if (isEcoOptimizationActive.value) "ECO Saving" else "Optimize Mode"
                        
                        info = "$batteryPct% | Temp: ${temperatureInCelsius}°C | V: ${voltage}mV | $speedPrefix"
                    }
                } catch (e: Exception) {
                    info = "98% | Temp: 28.1°C | V: 3750mV | ECO Saving"
                }
                localBatteryInfo.value = info
                delay(4000)
            }
        }
    }

    private fun stopSimulationJobs() {
        simulationSpeakerJob?.cancel()
        simulationSpeakerJob = null
    }

    private fun stopAllJobs() {
        stopSimulationJobs()
        batteryStatusJob?.cancel()
        batteryStatusJob = null
        audioEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopAllJobs()
        dismissNotification()
        if (instance == this) {
            instance = null
        }
    }

    private fun showOrUpdateNotification() {
        val channel = connectedChannel.value ?: return
        val muted = isMuted.value
        val context = getApplication<Application>().applicationContext
        
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val name = "Voice Chat Connection"
                val descriptionText = "Shows active voice chat channel status and controls"
                val importance = NotificationManager.IMPORTANCE_LOW
                val mChannel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(mChannel)
            }
            
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                openAppIntent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val toggleMuteIntent = Intent("com.example.ACTION_TOGGLE_MUTE").apply {
                setPackage(context.packageName)
            }
            val toggleMutePendingIntent = PendingIntent.getBroadcast(
                context,
                11,
                toggleMuteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val disconnectIntent = Intent("com.example.ACTION_DISCONNECT").apply {
                setPackage(context.packageName)
            }
            val disconnectPendingIntent = PendingIntent.getBroadcast(
                context,
                12,
                disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val statusText = if (muted) "Microphone Muted" else "Microphone Active (Transmitting)"
            val muteActionTitle = if (muted) "Unmute" else "Mute"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Connected to ${channel.name}")
                .setContentText(statusText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(openAppPendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Connected to ${channel.name}\n$statusText"))
                .addAction(
                    android.R.drawable.ic_media_play,
                    muteActionTitle,
                    toggleMutePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_delete,
                    "Disconnect",
                    disconnectPendingIntent
                )
            
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            updateTileState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissNotification() {
        val context = getApplication<Application>().applicationContext
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            updateTileState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTileState() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.quicksettings.TileService.requestListeningState(
                    getApplication(),
                    android.content.ComponentName(getApplication(), VoiceMuteTileService::class.java)
                )
            }
        } catch (e: Exception) {
            // ignore if not supported or missing
        }
    }

    companion object {
        @Volatile
        var instance: VoiceViewModel? = null
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "voice_chat_channel"
    }
}
