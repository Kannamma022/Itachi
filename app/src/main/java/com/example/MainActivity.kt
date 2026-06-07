package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChannelEntity
import com.example.data.ConnectionLogEntity
import com.example.data.ServerEntity
import com.example.ui.theme.*
import com.example.ui.GuestLoginScreen
import com.example.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: VoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    VoiceChatAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (viewModel.isPushToTalkEnabled.value) {
            val keyConfig = viewModel.pttTriggerKey.value
            val isMatch = if (keyConfig.contains("Volume Down")) {
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            } else {
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
            }
            if (isMatch) {
                viewModel.setPushToTalkActive(true)
                return true // Consume the volume shift to prevent popups
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (viewModel.isPushToTalkEnabled.value) {
            val keyConfig = viewModel.pttTriggerKey.value
            val isMatch = if (keyConfig.contains("Volume Down")) {
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            } else {
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
            }
            if (isMatch) {
                viewModel.setPushToTalkActive(false)
                return true // Consume the release
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun VoiceChatAppScreen(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe State Flows from ViewModel
    val serverList by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val channelList by viewModel.channels.collectAsStateWithLifecycle()
    val connectedChannel by viewModel.connectedChannel.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val recentLogs by viewModel.connectionLogs.collectAsStateWithLifecycle()

    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isDeafened by viewModel.isDeafened.collectAsStateWithLifecycle()
    val isLoopbackEnabled by viewModel.isLoopbackEnabled.collectAsStateWithLifecycle()
    val connectionLatency by viewModel.connectionLatency.collectAsStateWithLifecycle()

    // Audio Diagnostics
    val microphoneVolume by viewModel.microphoneVolume.collectAsStateWithLifecycle()
    val activeRate by viewModel.audioEngineSampleRate.collectAsStateWithLifecycle()
    val codecBitrateState by viewModel.codecBitrate.collectAsStateWithLifecycle()
    val ecoOptimizationActive by viewModel.isEcoOptimizationActive.collectAsStateWithLifecycle()
    val isGameBoosterEnabled by viewModel.isGameBoosterEnabled.collectAsStateWithLifecycle()
    val isNoiseSuppressionEnabled by viewModel.isNoiseSuppressionEnabledByUI.collectAsStateWithLifecycle()
    val activeNoiseProfile by viewModel.activeNoiseProfile.collectAsStateWithLifecycle()
    val voiceActivationThreshold by viewModel.voiceActivationThreshold.collectAsStateWithLifecycle()
    val isAnimeModeEnabled by viewModel.isAnimeModeEnabled.collectAsStateWithLifecycle()
    val activeAnimeSkin by viewModel.activeAnimeSkin.collectAsStateWithLifecycle()
    val isPushToTalkEnabled by viewModel.isPushToTalkEnabled.collectAsStateWithLifecycle()
    val isPushToTalkActive by viewModel.isPushToTalkActive.collectAsStateWithLifecycle()
    val pttTriggerKey by viewModel.pttTriggerKey.collectAsStateWithLifecycle()
    val performanceModeState by viewModel.performanceMode.collectAsStateWithLifecycle()
    val nativeBufferInfo by viewModel.nativeBufferInfo.collectAsStateWithLifecycle()
    val threadProcessUs by viewModel.threadProcessTimeUs.collectAsStateWithLifecycle()
    val localBatteryInfo by viewModel.localBatteryInfo.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeMembers.collectAsStateWithLifecycle()

    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val guestUsername by viewModel.guestUsername.collectAsStateWithLifecycle()

    // Permission handle with automatic immediate prompting on app opening
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = results[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        if (hasMicPermission && connectedChannel != null) {
            viewModel.joinVoiceChannel(connectedChannel!!)
        }
    }

    LaunchedEffect(Unit) {
        val list = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionsLauncher.launch(list)
    }

    // Modal dialog trigger for creating voice servers
    var showCreateServerDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (isLoggedIn) {
        Row(modifier = modifier.fillMaxSize()) {
        
        // ----------------- COLUMN 1: DISCORD SERVER DRAWER (Left Bar, 64.dp) -----------------
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Branded Application Circle Logo (Sasuke & Itachi Brotherhood logo)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C1E3C))
                    .clickable {
                        val funQuotes = listOf(
                            "Itachi: 'Forgive me Sasuke... Next time.' 🤜🌸",
                            "Sasuke: 'I'll become stronger than you! ...But pass the ramen first.' 🍜",
                            "Itachi: 'We are unique brothers. I will always be there for you.' 🦅🔥",
                            "Sasuke: 'Big brother, let's practice shuriken training together!' 🎯",
                            "Itachi: 'You still don't have enough bandwidth capacity, little brother... Toggle modern suppressors!' 🎙️"
                        )
                        android.widget.Toast.makeText(context, funQuotes.random(), android.widget.Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_brothers_talking_1780777560391),
                    contentDescription = "Itachi and Sasuke sharing a moment",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Divider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(0.6f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Dynamic list of active Room Servers
            LazyColumn(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(serverList) { server ->
                    val isSelected = selectedServer?.id == server.id
                    
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Discord-style dynamic colored capsule indicator on LHS
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .align(Alignment.CenterStart)
                            )
                        }

                        // Server Avatar Bubble
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(if (isSelected) RoundedCornerShape(14.dp) else CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondary
                                    else Color.White
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = if (isSelected) RoundedCornerShape(14.dp) else CircleShape
                                )
                                .clickable {
                                    viewModel.selectServer(server)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = server.iconChar,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Create New Server action click button
            IconButton(
                onClick = { showCreateServerDialog = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Server Button",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val userColorState by viewModel.guestAvatarColor.collectAsStateWithLifecycle()
            val userEmojiState by viewModel.guestAvatarEmoji.collectAsStateWithLifecycle()
            val userPicTypeState by viewModel.guestProfilePicType.collectAsStateWithLifecycle()

            val displayEmoji = if (userPicTypeState >= 0) {
                when (userPicTypeState) {
                    0 -> "🦊"
                    1 -> "⚡"
                    2 -> "👁️"
                    3 -> "👺"
                    4 -> "👥"
                    else -> userEmojiState
                }
            } else {
                userEmojiState
            }

            val displayColor = if (userPicTypeState >= 0) {
                when (userPicTypeState) {
                    0 -> 0xFFF97316
                    1 -> 0xFF3B82F6
                    2 -> 0xFFEF4444
                    3 -> 0xFF9333EA
                    4 -> 0xFFEC4899
                    else -> userColorState
                }
            } else {
                userColorState
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(displayColor))
                    .border(2.dp, Color(0xFF23A55A), CircleShape)
                    .clickable { showSettingsDialog = true }
                    .testTag("profile_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayEmoji,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Add structural vertical line between sidebar and details (Thin clean Border style)
        Divider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.outline
        )

        // ----------------- COLUMN 2: SERVER DETAIL / VOICE CHANNELS AREA (Middle/Right) -----------------
        val serverBgRes = when (selectedServer?.name) {
            "Konoha Leaf Village" -> R.drawable.img_naruto_bg_1780850474316
            "Uchiha Sanctuary" -> R.drawable.img_sasuke_bg_1780850489028
            "Akatsuki Fortress" -> R.drawable.img_itachi_bg_1780850504775
            "Madara's Infinite Realm" -> R.drawable.img_madara_bg_1780850520399
            else -> null
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0D061A))
        ) {
            if (serverBgRes != null) {
                Image(
                    painter = painterResource(id = serverBgRes),
                    contentDescription = "Anime Character Background",
                    modifier = Modifier.fillMaxSize().alpha(0.18f),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0D061A).copy(alpha = 0.5f),
                                    Color(0xFF0D061A).copy(alpha = 0.95f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                
                // Channel Header Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedServer?.name ?: "No Server Active",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connectionStatus == "Connected") Color(0xFF23A55A) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (connectionStatus == "Connected") "LOW LATENCY MODE" else "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Invite Button Option (Visual indicator)
                if (selectedServer != null) {
                    TextButton(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("invite_friends_header_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("👥", fontSize = 14.sp)
                            Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Call Latency Badge
                if (connectionStatus == "Connected" && connectedChannel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF23A55A).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF23A55A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF23A55A))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${connectionLatency}ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF23A55A)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Gray.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Offline",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Bottom border line for header toolbar
            Divider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // High priority Micro-permission advice block
            if (!hasMicPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎙️ Microphone input is restricted",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "NAW TALKING APP needs audio access to capture raw low-overhead voice packets. (Simulation engine running as backup)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Enable Audio", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // Main Contents Scaffold: LazyColumn of channels + Diagnostics & persistent configurations
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dynamic list of active Channels grouped in a beautiful Card (Clean Minimalism Design HTML structure)
                item {
                    val activeCount = channelList.count { connectedChannel?.id == it.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row inside Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VOICE CHANNELS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.0.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (activeCount > 0) "$activeCount Active" else "No Active Channels",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (channelList.isEmpty()) {
                                Text(
                                    text = "No channels configured. Deploy new guilds using the LHS bar.",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    channelList.forEach { channel ->
                                        val isConnected = connectedChannel?.id == channel.id
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isConnected) MaterialTheme.colorScheme.secondary
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    if (isConnected) {
                                                        viewModel.leaveVoiceChannel()
                                                    } else {
                                                        viewModel.joinVoiceChannel(channel)
                                                    }
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isConnected) "🔊" else "🔇",
                                                    fontSize = 16.sp,
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                                
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = channel.name,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${channel.latencyCategory} Latency | Channel Space",
                                                        fontSize = 10.sp,
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }

                                                // Clean Join status or Disconnect button
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isConnected) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary
                                                        )
                                                        .clickable {
                                                            if (isConnected) {
                                                                viewModel.leaveVoiceChannel()
                                                            } else {
                                                                viewModel.joinVoiceChannel(channel)
                                                            }
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isConnected) "Leave" else "Join",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        // Speaking member layout if connected
                                        if (isConnected) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp, end = 4.dp, bottom = 8.dp, top = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                activeMembers.forEach { member ->
                                                    val infiniteTransition = rememberInfiniteTransition(label = "audio_ripple")
                                                    val auraScale by infiniteTransition.animateFloat(
                                                        initialValue = 1.0f,
                                                        targetValue = 1.6f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(1100, easing = LinearEasing),
                                                            repeatMode = RepeatMode.Restart
                                                        ),
                                                        label = "scale"
                                                    )
                                                    val auraAlpha by infiniteTransition.animateFloat(
                                                        initialValue = 0.8f,
                                                        targetValue = 0.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(1100, easing = LinearEasing),
                                                            repeatMode = RepeatMode.Restart
                                                        ),
                                                        label = "alpha"
                                                    )

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                if (isAnimeModeEnabled) Color(0xFF1C132E).copy(alpha = 0.7f) 
                                                                else Color.White.copy(alpha = 0.5f), 
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (member.isSpeaking && isAnimeModeEnabled) Color(member.avatarColor).copy(alpha = 0.5f)
                                                                        else Color.Transparent,
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            if (member.isSpeaking && isAnimeModeEnabled) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .graphicsLayer {
                                                                            scaleX = auraScale
                                                                            scaleY = auraScale
                                                                            alpha = auraAlpha
                                                                        }
                                                                        .clip(CircleShape)
                                                                        .background(Color(member.avatarColor))
                                                                )
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(member.avatarColor))
                                                                    .border(
                                                                        width = if (member.isSpeaking) 1.5.dp else 0.dp,
                                                                        color = if (member.isSpeaking) {
                                                                            if (isAnimeModeEnabled) Color(member.avatarColor) else Color(0xFF23A55A)
                                                                        } else Color.Transparent,
                                                                        shape = CircleShape
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = member.name.take(1),
                                                                    fontSize = 9.sp,
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        Text(
                                                            text = member.name,
                                                            fontSize = 12.sp,
                                                            color = if (member.isSpeaking) {
                                                                if (isAnimeModeEnabled) Color(member.avatarColor) else Color(0xFF23A55A)
                                                            } else {
                                                                if (isAnimeModeEnabled) Color.White else MaterialTheme.colorScheme.onSecondary
                                                            },
                                                            fontWeight = if (member.isSpeaking) FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        Text(
                                                            text = "${member.deviceStatus} • ${member.latencyMs}ms",
                                                            fontSize = 9.sp,
                                                            color = if (isAnimeModeEnabled) Color(0xFFA78BFA).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------- HARDWARE OPTIMIZER MODULE -----------------
                item {
                    Text(
                        text = "HARDWARE OPTIMIZATION STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                    )
                }

                // ----------------- ZERO-LAG GAMING OPTIMIZER BOOSTER -----------------
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGameBoosterEnabled) {
                                Color(0xFF143020) // Deep rich green for active gaming booster
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isGameBoosterEnabled) Color(0xFF23A55A).copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎮 Zero-Lag Gaming Booster Mode",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGameBoosterEnabled) Color(0xFF23A55A) else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isGameBoosterEnabled,
                                    onCheckedChange = { viewModel.toggleGameBooster() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF23A55A),
                                        checkedTrackColor = Color(0xFF143020)
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = "Optimizes speech processing using THREAD_PRIORITY_URGENT_AUDIO and halves the audio kernel packet buffers. Reduces latency and prevents frame dropouts, dedicating max CPU cycles and extreme scheduling priority to heavy background games like Free Fire, PUBG, Roblox, or Mobile Legends.",
                                fontSize = 11.sp,
                                color = if (isGameBoosterEnabled) Color.White.copy(alpha = 0.9f) else Color.Gray,
                                lineHeight = 14.sp
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isGameBoosterEnabled) Color.Black.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isGameBoosterEnabled) Color(0xFF23A55A) else Color.Yellow)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isGameBoosterEnabled) "ACTIVE EXCLUSIVE PRIORITY" else "STANDARD THREAD LAYOUT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGameBoosterEnabled) Color(0xFF23A55A) else Color.Gray
                                    )
                                }
                                
                                Text(
                                    text = if (isGameBoosterEnabled) "Buffer: 512B Frame • Low Jitter" else "Buffer: 1024B Frame • Normal Jitter",
                                    fontSize = 9.sp,
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // ----------------- PUSH-TO-TALK (PTT) GAME CHATTER MODULE -----------------
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ptt_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPushToTalkEnabled) {
                                Color(0xFF2C1E3C) // Elegant deep cosmic violet/purple
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPushToTalkEnabled) Color(0xFFA855F7).copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ Real-Time Push-To-Talk (PTT)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPushToTalkEnabled) Color(0xFFC084FC) else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isPushToTalkEnabled,
                                    onCheckedChange = { viewModel.togglePushToTalk() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFA855F7),
                                        checkedTrackColor = Color(0xFF2C1E3C)
                                    ),
                                    modifier = Modifier.testTag("ptt_switch")
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = "Conserves mobile data plan bandwidth and cuts background gaming room chatter. The microphone remains completely muted unless you hold the selected hardware trigger key or the on-screen action bar.",
                                fontSize = 11.sp,
                                color = if (isPushToTalkEnabled) Color.White.copy(alpha = 0.9f) else Color.Gray,
                                lineHeight = 14.sp
                            )
                            
                            if (isPushToTalkEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Hardware key selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Hardware Trigger Key:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.LightGray
                                    )
                                    Button(
                                        onClick = { viewModel.togglePTTTriggerKey() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF3B2A50)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("ptt_key_toggle_button")
                                    ) {
                                        Text(
                                            text = pttTriggerKey,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE9D5FF)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Interactive hold-to-talk button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isPushToTalkActive) {
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                                                )
                                            } else {
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF3B2A50), Color(0xFF2E1F3F))
                                                )
                                            }
                                        )
                                        .testTag("hold_to_talk_button")
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.setPushToTalkActive(true)
                                                    try {
                                                        tryAwaitRelease()
                                                    } finally {
                                                        viewModel.setPushToTalkActive(false)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (isPushToTalkActive) "🎙️" else "💤",
                                            fontSize = 20.sp,
                                            modifier = Modifier
                                                .scale(if (isPushToTalkActive) 1.2f else 1.0f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isPushToTalkActive) "MIC TRANSMITTING NOW..." else "HOLD TO TRANSMIT",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPushToTalkActive) Color.White else Color.Gray,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPushToTalkEnabled) Color.Black.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isPushToTalkEnabled) {
                                                if (isPushToTalkActive) Color(0xFFA855F7) else Color.Red
                                            } else Color.Yellow)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPushToTalkEnabled) {
                                            if (isPushToTalkActive) "TRANSMITTING VOICE (Active)" else "MICROPHONE MUTED (Idle)"
                                        } else "CONTINUOUS BROADCAST ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPushToTalkEnabled) {
                                            if (isPushToTalkActive) Color(0xFFC084FC) else Color.Gray
                                        } else Color.Gray
                                    )
                                }
                                
                                Text(
                                    text = if (isPushToTalkEnabled) {
                                        if (isPushToTalkActive) "Mode: Push To Talk • Active" else "Mode: Push To Talk • Muted"
                                    } else "Mode: Always On",
                                    fontSize = 9.sp,
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // ----------------- HARDWARE REALTIME DIAGNOSTICS & SYSTEM METRICS -----------------
                item {
                    Text(
                        text = "HARDWARE ENGINE TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔒 Secure Low-Level Engine Diagnostics",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Parameter outputs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Telemetry Profile:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = "$performanceModeState Profile",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (performanceModeState == "Ultra Eco") Color(0xFF23A55A) else MaterialTheme.colorScheme.secondary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Battery Status (Real/HW):", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = localBatteryInfo,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Audio Track Buffer Info:", fontSize = 11.sp, color = Color.Gray)
                                Text(text = nativeBufferInfo, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Processing Thread Latency:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = if (connectionStatus == "Connected") "${threadProcessUs}μs (Low Bus Load)" else "0μs (Sleeping)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF23A55A),
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // ----------------- CONNECTION AUDITING HISTORY LOGS (Room Persisted) -----------------
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONNECTION LOG AUDITS (ROOM PERSISTED)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.0.sp
                        )
                        
                        Text(
                            text = "🧹 Clear logs",
                            fontSize = 11.sp,
                            color = Color(0xFFF23F43),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                coroutineScope.launch { viewModel.clearLogs() }
                            }
                        )
                    }
                }

                if (recentLogs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = "Database log empty. Connect to voice channels to write local audit events.",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(recentLogs) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.serverName} • ${log.channelName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${log.performanceMode} • ${log.codecBitrateKbps}kbps | ${log.deviceStatus}",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Ping: ${log.latencyMs}ms",
                                        fontSize = 11.sp,
                                        color = Color(0xFF23A55A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Loss: ${(log.packetLossPercent * 100).toInt()}%",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ----------------- ROW 3: DISCORD DOCK / COZY CALL POD (Fixed Bottom Control Bar) -----------------
            AnimatedVisibility(
                visible = connectionStatus != "Disconnected" && connectedChannel != null,
                enter = slideInVertically(animationSpec = spring()) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = SlateDarkest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        
                        // Waveform/Audio Level display row (optimized)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Dot Indicator
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF23A55A))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "TRANSMITTING INPUT ON ${connectedChannel?.name?.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF23A55A),
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Buffer stat or ECO state indicator
                            Text(
                                text = if (isGameBoosterEnabled) "GAME BOOST ACTIVE" else if (ecoOptimizationActive) "ECO LEVEL FIXED" else "LIVE TRANSCRIBING",
                                fontSize = 9.sp,
                                color = if (isGameBoosterEnabled) Color(0xFF23A55A) else MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // HIGH PERFORMANCE AMPLITUDE GRAPHIC CANVAS
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateMiddle)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ecoOptimizationActive || isGameBoosterEnabled) {
                                // Low-cost ECO Rendering (Absolutely zero dynamic wave computations to save older CPUs)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .scale(if (microphoneVolume > 0.05) 1.5f else 1.0f)
                                            .clip(CircleShape)
                                            .background(if (microphoneVolume > 0.05) Color(0xFF23A55A) else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (microphoneVolume > 0.05) "VOICE INTENSITY: ACTIVE SPEAKER" else "VOICE INTENSITY: SILENT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (microphoneVolume > 0.05) Color.White else Color.Gray
                                    )
                                }
                            } else {
                                // High-Performance custom Canvas PCM waveform animator
                                val infiniteTransition = rememberInfiniteTransition()
                                val waveFactor by infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.4f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val count = 28
                                    val barWidth = 6.dp.toPx()
                                    val gap = 4.dp.toPx()
                                    val centerY = size.height / 2
                                    
                                    val startOffset = (size.width - (count * (barWidth + gap))) / 2

                                    for (i in 0 until count) {
                                        // Standard mathematical sine distribution configured with user speaking level
                                        val distCenter = 1f - (Math.abs(i - count / 2f) / (count / 2f))
                                        val barHeightHeight = (microphoneVolume * 80.dp.toPx() * distCenter * waveFactor)
                                            .coerceAtLeast(3.dp.toPx())

                                        val x = startOffset + i * (barWidth + gap)
                                        
                                        drawRoundRect(
                                            color = if (isMuted) Color.Gray else Color(0xFF23A55A),
                                            topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barHeightHeight / 2),
                                            size = androidx.compose.ui.geometry.Size(barWidth, barHeightHeight),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Controls Panel: Mute, Deafen, Loopback, Disconnect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            
                            // Button 1: Mute
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = { viewModel.toggleMute() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMuted) Color(0xFFF23F43) else SlateLighter
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.size(46.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (isMuted) "🔇" else "🎙️",
                                        fontSize = 18.sp
                                    )
                                }
                                Text("Mute", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }

                            // Button 2: Deafen
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = { viewModel.toggleDeafen() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDeafened) Color(0xFFF23F43) else SlateLighter
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.size(46.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (isDeafened) "🔇" else "🎧",
                                        fontSize = 18.sp
                                    )
                                }
                                Text("Deafen", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }

                            // Button 3: Local Loopback
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = { viewModel.toggleLoopback() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLoopbackEnabled) Color(0xFF5865F2) else SlateLighter
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.size(46.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "🔊",
                                        fontSize = 18.sp,
                                        color = if (isLoopbackEnabled) Color.White else Color.Gray
                                    )
                                }
                                Text("Monitor", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }

                            // Button 4: DISCONNECT CALL
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = { viewModel.leaveVoiceChannel() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF23F43)
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.size(46.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "🛑",
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                                Text("Hangup", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            } // Closes AnimatedVisibility (ROW 3)
        } // Closes Column 2 inner Column
    } // Closes Column 2 outer Box
} // Closes root content Row
} // Closes if (isLoggedIn) block
else {
    GuestLoginScreen(viewModel = viewModel, modifier = modifier)
}

    // Modal dialog to implement custom Discord voice server creations locally
    if (showCreateServerDialog) {
        var newServerName by remember { mutableStateOf("") }
        var selectedIconChar by remember { mutableStateOf("🎮") }
        val icons = listOf("🎮", "💻", "🎵", "⚡", "👾", "🦊", "👑", "🍕", "🔥", "🔮")

        Dialog(onDismissRequest = { showCreateServerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp), // Modern minimalist curved cards
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Modern Voice Server",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = newServerName,
                        onValueChange = { newServerName = it },
                        placeholder = { Text("Server Name...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Pick Glyph Avatar Category Icon", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        icons.forEach { icon ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedIconChar == icon) MaterialTheme.colorScheme.primary
                                        else SlateMiddle
                                    )
                                    .clickable { selectedIconChar = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(icon, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateServerDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newServerName.isNotEmpty()) {
                                    // Save server dynamically to persistent database!
                                    coroutineScope.launch {
                                        val serverDao = AppDatabase.getDatabase(context).serverDao()
                                        val channelDao = AppDatabase.getDatabase(context).channelDao()
                                        
                                        val newServerId = serverDao.insertServer(
                                            ServerEntity(
                                                name = newServerName,
                                                iconChar = selectedIconChar,
                                                description = "Local voice community."
                                            )
                                        )
                                        
                                        // Insert default essential lobbies automatically
                                        channelDao.insertChannels(
                                            listOf(
                                                ChannelEntity(
                                                    serverId = newServerId,
                                                    name = "General Lounge 🗣️",
                                                    onlineCount = 1,
                                                    latencyCategory = "Ultra Low"
                                                ),
                                                ChannelEntity(
                                                    serverId = newServerId,
                                                    name = "Gaming Lobby 🔫",
                                                    onlineCount = 0,
                                                    latencyCategory = "Low"
                                                )
                                            )
                                        )
                                        showCreateServerDialog = false
                                        newServerName = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // Invite Dialog
    if (showInviteDialog && selectedServer != null) {
        val server = selectedServer!!
        val inviteLink = "https://nawtalking.app/invite/${server.name.lowercase().replace(" ", "-")}-${server.id}"
        var invitedFriends by remember { mutableStateOf(setOf<String>()) }

        Dialog(onDismissRequest = { showInviteDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("invite_friends_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "👥 Invite Friends",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Server: ${server.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SHAREABLE INVITATION LINK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = inviteLink,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        Text(
                            text = "📋 Copy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Server Invite Link", inviteLink)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Link copied to clipboard! 🔗", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("copy_invite_link_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "SELECT FRIENDS TO DIRECTLY INVITE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val friends = listOf(
                        Pair("Naruto Uzumaki 🦊", "Online"),
                        Pair("Sakura Haruno 🌸", "Idle"),
                        Pair("Kakashi Hatake 🦅", "Coding"),
                        Pair("Shisui Uchiha 🦅", "Training")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        friends.forEach { (friendName, status) ->
                            val isInvited = invitedFriends.contains(friendName)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = friendName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = status,
                                        fontSize = 10.sp,
                                        color = if (status == "Online") Color(0xFF23A55A) else Color.Gray,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (!isInvited) {
                                            invitedFriends = invitedFriends + friendName
                                            android.widget.Toast.makeText(context, "Invitation sent to $friendName! 🌠", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInvited) Color(0xFF23A55A) else MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("invite_button_${friendName.replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = if (isInvited) "Invited ✨" else "Invite",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showInviteDialog = false },
                            modifier = Modifier.testTag("close_invite_dialog")
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    // Logout Dialog Confirmation
    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout Profile") },
            text = { Text("Are you sure you want to sign out from guest account '$guestUsername'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmation = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("logout_confirmation_dialog")
        )
    }

    // Application Settings Dialog
    if (showSettingsDialog) {
        val userColorState by viewModel.guestAvatarColor.collectAsStateWithLifecycle()
        val userEmojiState by viewModel.guestAvatarEmoji.collectAsStateWithLifecycle()
        val userPicTypeState by viewModel.guestProfilePicType.collectAsStateWithLifecycle()
        val isNoiseSuppressionEnabled by viewModel.isNoiseSuppressionEnabledByUI.collectAsStateWithLifecycle()
        val activeNoiseProfile by viewModel.activeNoiseProfile.collectAsStateWithLifecycle()
        val voiceActivationThreshold by viewModel.voiceActivationThreshold.collectAsStateWithLifecycle()

        val micVolMultiplier by viewModel.micVolumeMultiplier.collectAsStateWithLifecycle()
        val playVolMultiplier by viewModel.playbackVolumeMultiplier.collectAsStateWithLifecycle()
        val isEchoEnabled by viewModel.isLocalEchoEnabled.collectAsStateWithLifecycle()
        val isAgcEnabled by viewModel.isAgcEnabled.collectAsStateWithLifecycle()
        val activeVoiceEffect by viewModel.activeVoiceEffect.collectAsStateWithLifecycle()

        var tempUsername by remember { mutableStateOf(guestUsername) }
        var tempPicType by remember { mutableStateOf(userPicTypeState) }
        var tempColor by remember { mutableStateOf(userColorState) }
        var tempEmoji by remember { mutableStateOf(userEmojiState) }

        // Sync local edit states when opened
        LaunchedEffect(showSettingsDialog) {
            tempUsername = guestUsername
            tempPicType = userPicTypeState
            tempColor = userColorState
            tempEmoji = userEmojiState
        }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚙️ Application Settings", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { showSettingsDialog = false }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = Color.White
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- SECTION 1: PROFILE MANAGEMENT ---
                    Text(
                        text = "USER PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        label = { Text("Display Username") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_username_input"),
                        singleLine = true
                    )

                    // Profile Pic Preset selectors (Naruto characters)
                    Text(
                        text = "Select Preset Anime Profile Picture:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    val presets = listOf(
                        Triple(0, "🦊 Naruto", 0xFFF97316),
                        Triple(1, "⚡ Sasuke", 0xFF3B82F6),
                        Triple(2, "👁️ Itachi", 0xFFEF4444),
                        Triple(3, "👺 Madara", 0xFF9333EA),
                        Triple(4, "👥 UCHIHA BROTHERS", 0xFFEC4899),
                        Triple(-1, "🎨 Custom", tempColor)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (typeVal, label, colorVal) ->
                            val isSelected = tempPicType == typeVal
                            val borderCol = if (isSelected) Color(colorVal) else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(colorVal).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(2.dp, borderCol, RoundedCornerShape(8.dp))
                                    .clickable {
                                        tempPicType = typeVal
                                        if (typeVal >= 0) {
                                            tempColor = colorVal
                                            tempEmoji = when (typeVal) {
                                                0 -> "🦊"
                                                1 -> "⚡"
                                                2 -> "👁️"
                                                3 -> "👺"
                                                4 -> "👥"
                                                else -> "👤"
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.Gray
                                )
                            }
                        }
                    }

                    if (tempPicType == -1) {
                        // Custom avatar controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tempEmoji,
                                onValueChange = { tempEmoji = it.take(2) },
                                label = { Text("Emoji Avatar") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // Quick custom color choices
                            val colorsList = listOf(0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF009688, 0xFF4CAF50, 0xFFFFC107)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom Color", fontSize = 10.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    colorsList.forEach { col ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(col))
                                                .border(
                                                    2.dp,
                                                    if (tempColor == col) Color.White else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable { tempColor = col }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.3f))

                    // --- SECTION 2: NOISE CANCELLATION ---
                    Text(
                        text = "NOISE CANCELLATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Acoustic Noise Suppressor", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Continuously filters background noise.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isNoiseSuppressionEnabled,
                            onCheckedChange = { viewModel.toggleNoiseSuppression() }
                        )
                    }

                    if (isNoiseSuppressionEnabled) {
                        Text("Appliance Hum Filtering Preset:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        val noiseList = listOf(
                            Pair("STANDARD", "🎙️ Standard Gate"),
                            Pair("FAN", "💨 Fan Hum"),
                            Pair("AC", "❄️ AC Hiss"),
                            Pair("WASHING", "🧼 Washer"),
                            Pair("MIXER", "🌪️ Mixer")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            noiseList.forEach { (profileKey, label) ->
                                val isSelected = activeNoiseProfile == profileKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.setNoiseProfile(profileKey) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else Color.LightGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gating sensitivity: ${(voiceActivationThreshold * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Slider(
                            value = voiceActivationThreshold,
                            onValueChange = { viewModel.setVoiceActivationThreshold(it) },
                            valueRange = 0f..0.60f
                        )
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.3f))

                    // --- SECTION 3: AUDIO CONTROLS ---
                    Text(
                        text = "AUDIO CONTROLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Microphone Out Gain Coeff", fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("${(micVolMultiplier * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Slider(
                            value = micVolMultiplier,
                            onValueChange = { viewModel.setMicVolumeMultiplier(it) },
                            valueRange = 0.5f..3.0f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Incoming Playback Master Volume", fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("${(playVolMultiplier * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Slider(
                            value = playVolMultiplier,
                            onValueChange = { viewModel.setPlaybackVolumeMultiplier(it) },
                            valueRange = 0.0f..2.0f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Local Loopback Echo", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Feed microphone back into speakers.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isEchoEnabled,
                            onCheckedChange = { viewModel.toggleLocalEcho() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Gain Control (AGC)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Dynamically normalize mic volume to keep transmission levels smooth and consistent.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isAgcEnabled,
                            onCheckedChange = { viewModel.toggleAgc() },
                            modifier = Modifier.testTag("agc_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))

                    // --- SECTION 4: SHINOBI VOICE MODULATION ---
                    Text(
                        text = "SHINOBI VOICE MODULATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Choose your character-themed real-time DSP voice filter for an immersive, lore-faithful gaming experience. Pair this with the loopback echo setting above to hear your transformed voice.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )

                        val voiceEffects = listOf(
                            Triple("NONE", "Raw Mic 🎙️", "No transformation applied, regular clean sound."),
                            Triple("NARUTO", "Naruto Uzumaki 🦊", "Vibrant, high-energy raspy shadow frequency pitch boost."),
                            Triple("OBITO", "Obito & Kamui DSP 👺", "Deep, slow, hollow spatial distortion with Kamui echo."),
                            Triple("ITACHI", "Itachi Tsukuyomi 🦅👀", "Calm, slow majestic pitch reduction with Tsukuyomi echo.")
                        )

                        voiceEffects.forEach { (key, title, desc) ->
                            val isSelected = activeVoiceEffect == key
                            val containerCol = if (isSelected) {
                                when(key) {
                                    "NARUTO" -> Color(0xFF1E1712) // dark orange tint
                                    "OBITO" -> Color(0xFF1C132E) // dark purple tint
                                    "ITACHI" -> Color(0xFF1D1111) // dark red tint
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                            val borderCol = if (isSelected) {
                                when(key) {
                                    "NARUTO" -> Color(0xFFF97316)
                                    "OBITO" -> Color(0xFF9333EA)
                                    "ITACHI" -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            } else {
                                Color.Transparent
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(containerCol)
                                    .border(1.5.dp, borderCol, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setVoiceEffect(key) }
                                    .padding(12.dp)
                                    .testTag("voice_effect_row_$key"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) Color.White else Color.LightGray
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(borderCol)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                                
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setVoiceEffect(key) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = borderCol,
                                        unselectedColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("voice_effect_radio_$key").size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(tempUsername, tempPicType, tempColor, tempEmoji)
                        showSettingsDialog = false
                    },
                    modifier = Modifier.testTag("apply_settings_button")
                ) {
                    Text("Apply & Save Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("Close")
                }
            },
            modifier = Modifier.testTag("settings_modal_dialog")
        )
    }
}
