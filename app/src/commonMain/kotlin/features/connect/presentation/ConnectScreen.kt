package app.duinch.agentremote.features.connect.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.presentation.util.currentTimeMillis
import app.duinch.agentremote.core.presentation.util.rememberPlatformQrScanner
import app.duinch.agentremote.features.connect.data.LocalServerScanner
import app.duinch.agentremote.features.connect.domain.model.DiscoveredServer
import app.duinch.agentremote.features.connect.presentation.DiscoveredServerBanner
import app.duinch.agentremote.features.settings.data.PlatformSettingsStorage
import app.duinch.remotetunnel.parser.RemoteQrPayloadParser
import app.duinch.remotetunnel.ui.ConnectMode
import app.duinch.remotetunnel.ui.ConnectionTelemetryBar
import app.duinch.remotetunnel.ui.DualChannelConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel
import kotlinx.coroutines.delay

@Composable
fun ConnectScreen(
    currentHost: String,
    connectionStatus: ConnectionStatus,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val qrScanner = rememberPlatformQrScanner()
    var connectedAtEpochMs by rememberSaveable { mutableStateOf(0L) }
    var hasPrimarySlot by rememberSaveable(currentHost) { mutableStateOf(currentHost.isNotBlank()) }
    var primarySlotMode by remember(currentHost) {
        mutableStateOf(
            if (currentHost.contains("trycloudflare.com") || currentHost.startsWith("https://") || currentHost.startsWith("wss://")) {
                ConnectMode.Remote
            } else {
                ConnectMode.Lan
            }
        )
    }
    var extraSlots by remember { mutableStateOf(emptyList<AgentConnectionSlot>()) }
    var nextSlotIndex by remember { mutableStateOf(2) }
    var showAddSlotPicker by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf(emptyList<DiscoveredServer>()) }
    var recentTunnels by remember {
        mutableStateOf(
            PlatformSettingsStorage().load().recentTunnels.ifEmpty {
                if (currentHost.contains("trycloudflare.com")) listOf(currentHost) else emptyList()
            }
        )
    }

    LaunchedEffect(Unit) {
        try {
            discoveredServers = LocalServerScanner().scan(recentTunnels)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus is ConnectionStatus.Connected) {
            if (connectedAtEpochMs == 0L) {
                connectedAtEpochMs = currentTimeMillis()
            }
        } else if (connectionStatus is ConnectionStatus.Disconnected) {
            connectedAtEpochMs = 0L
        }
    }

    val nowEpochMs by produceState(
        initialValue = currentTimeMillis(),
        key1 = connectedAtEpochMs
    ) {
        if (connectedAtEpochMs > 0L) {
            while (true) {
                value = currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val telemetry = remember(connectionStatus, currentHost, nowEpochMs) {
        buildAgentTelemetry(connectionStatus, currentHost, connectedAtEpochMs, nowEpochMs)
    }

    val initialLan = remember(currentHost) {
        when {
            currentHost.contains(":") && !currentHost.contains("trycloudflare.com") && !currentHost.startsWith("https://") -> currentHost
            currentHost.isNotBlank() && !currentHost.contains("trycloudflare.com") && !currentHost.startsWith("https://") -> currentHost
            else -> ""
        }
    }

    val initialRemote = remember(currentHost) {
        if (currentHost.contains("trycloudflare.com") || currentHost.startsWith("https://")) currentHost else ""
    }

    val triggerQrScan = {
        qrScanner.startScan { raw ->
            val parsed = RemoteQrPayloadParser.parse(raw)
            val target = parsed?.url?.takeIf { it.isNotBlank() }
                ?: parsed?.lan?.takeIf { it.isNotBlank() }
                ?: raw.trim()
            if (target.isNotBlank()) {
                val isRemote = target.contains("trycloudflare.com") || target.startsWith("https://") || target.startsWith("wss://")
                primarySlotMode = if (isRemote) ConnectMode.Remote else ConnectMode.Lan
                hasPrimarySlot = true
                if (isRemote) {
                    recentTunnels = (listOf(target) + recentTunnels).distinct().take(3)
                }
                onConnect(target)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF141416),
        topBar = {
            ConnectTopBar(
                onScanQr = triggerQrScan,
                onOpenSettings = onOpenSettings
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F11))
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isMultiColumn = maxWidth >= 860.dp
            val contentMaxWidth = if (isMultiColumn) 1040.dp else 560.dp

            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val showPrimary = hasPrimarySlot || telemetry.state != RemoteConnectionState.Disconnected

                val connectionSlotsContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hiển thị máy chủ LAN / Cục bộ được tự động dò thấy khi chưa kết nối
                        if (telemetry.state == RemoteConnectionState.Disconnected) {
                            discoveredServers.forEach { server ->
                                DiscoveredServerBanner(
                                    server = server,
                                    onConnect = { host ->
                                        val prefs = PlatformSettingsStorage()
                                        val cur = prefs.load()
                                        prefs.save(cur.copy(lastGatewayUrl = host, autoReconnect = true))
                                        onConnect(host)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Unified Telemetry Ribbon (Bento Bar) - chỉ hiển thị khi đang kết nối hoặc đã kết nối
                        if (telemetry.state != RemoteConnectionState.Disconnected) {
                            ConnectionTelemetryBar(
                                telemetry = telemetry,
                                onTriggerConnect = triggerQrScan
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        if (!showPrimary && extraSlots.isEmpty()) {
                            EmptyAgentConnectionCard(modifier = Modifier.fillMaxWidth())
                        }

                        // Máy Chủ 1 (Kết Nối Máy Chủ Chính)
                        if (showPrimary) {
                            DualChannelConnectContent(
                                title = if (extraSlots.isNotEmpty()) "Máy Chủ 1" else "Kết Nối Máy Chủ",
                                dedicatedMode = primarySlotMode,
                                initialLanAddress = initialLan,
                                initialRemoteTarget = initialRemote,
                                telemetry = telemetry,
                                recentTunnels = recentTunnels,
                                onDeleteRecentTunnel = { deletedUrl ->
                                    recentTunnels = recentTunnels.filterNot { it == deletedUrl }
                                    val prefs = PlatformSettingsStorage()
                                    val cur = prefs.load()
                                    val updatedUrl = if (cur.lastGatewayUrl == deletedUrl) "" else cur.lastGatewayUrl
                                    prefs.save(cur.copy(lastGatewayUrl = updatedUrl, recentTunnels = recentTunnels))
                                },
                                onClose = {
                                    onDisconnect()
                                    hasPrimarySlot = false
                                    val prefs = PlatformSettingsStorage()
                                    val cur = prefs.load()
                                    prefs.save(cur.copy(lastGatewayUrl = "", recentTunnels = emptyList()))
                                    recentTunnels = emptyList()
                                },
                                onConnect = { lan, remote ->
                                    val isUrl = remote.startsWith("http://") || remote.startsWith("https://") || remote.contains("trycloudflare.com")
                                    val target = when {
                                        lan.isNotBlank() -> lan
                                        isUrl -> remote
                                        remote.isNotBlank() -> remote
                                        else -> currentHost
                                    }
                                    if (target.isNotBlank()) {
                                        if (target.contains("trycloudflare.com")) {
                                            recentTunnels = (listOf(target) + recentTunnels).distinct().take(3)
                                        }
                                        val prefs = PlatformSettingsStorage()
                                        val cur = prefs.load()
                                        prefs.save(cur.copy(lastGatewayUrl = target, recentTunnels = recentTunnels))
                                        onConnect(target)
                                    }
                                },
                                onDisconnect = onDisconnect,
                                onScanQr = triggerQrScan
                            )
                        }

                        // Danh sách các Máy Chủ kết nối phụ (Được thêm qua [+ Thêm máy chủ kết nối])
                        extraSlots.forEachIndexed { index, slot ->
                            if (showPrimary || index > 0) {
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                            val slotNum = if (showPrimary) index + 2 else index + 1
                            val slotTitle = if (!showPrimary && extraSlots.size == 1) "Kết Nối Máy Chủ" else "Máy Chủ $slotNum"
                            val isThisSlotConnected = (currentHost == slot.serverUrlInput && currentHost.isNotBlank()) ||
                                (currentHost == slot.remoteRoomCode && currentHost.isNotBlank())
                            val slotConnState = if (isThisSlotConnected) telemetry.state else RemoteConnectionState.Disconnected
                            val slotTelemetry = remember(slot.serverUrlInput, slot.remoteRoomCode, slotConnState) {
                                RemoteTelemetryData(
                                    state = slotConnState,
                                    channel = if (slot.mode == ConnectMode.Lan) RemoteTransportChannel.LanWifi else RemoteTransportChannel.QuickTunnel,
                                    host = slot.serverUrlInput,
                                    roomCode = slot.remoteRoomCode
                                )
                            }
                            DualChannelConnectContent(
                                title = slotTitle,
                                dedicatedMode = slot.mode,
                                initialLanAddress = slot.serverUrlInput,
                                initialRemoteTarget = slot.remoteRoomCode,
                                telemetry = slotTelemetry,
                                recentTunnels = recentTunnels,
                                onDeleteRecentTunnel = { deletedUrl ->
                                    recentTunnels = recentTunnels.filterNot { it == deletedUrl }
                                    val prefs = PlatformSettingsStorage()
                                    val cur = prefs.load()
                                    val updatedUrl = if (cur.lastGatewayUrl == deletedUrl) "" else cur.lastGatewayUrl
                                    prefs.save(cur.copy(lastGatewayUrl = updatedUrl, recentTunnels = recentTunnels))
                                },
                                onClose = { extraSlots = extraSlots.filterNot { it.id == slot.id } },
                                onConnect = { lan, remote ->
                                    val target = if (lan.isNotBlank()) lan else remote
                                    if (target.isNotBlank()) {
                                        if (target.contains("trycloudflare.com") || target.matches(Regex("""^\d{3}-?\d{3}$"""))) {
                                            recentTunnels = (listOf(target) + recentTunnels).distinct().take(3)
                                        }
                                        val prefs = PlatformSettingsStorage()
                                        val cur = prefs.load()
                                        prefs.save(cur.copy(lastGatewayUrl = target, recentTunnels = recentTunnels))
                                        onConnect(target)
                                    }
                                },
                                onDisconnect = onDisconnect,
                                onScanQr = triggerQrScan
                            )
                        }
                    }
                }

                val helperActionsContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showAddSlotPicker = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thêm máy chủ kết nối", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E1E22),
                            border = BorderStroke(1.dp, Color(0xFF2E2E34))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "⚡ Đa Kênh Tự Động (Dual-Channel)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEEEEF0))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "• WebRTC Direct DataChannel: Kết nối P2P độ trễ cực thấp (< 30ms)\n• Cloudflare Quick Tunnel: Vượt NAT / Firewall mọi lúc mọi nơi\n• Tự động kết nối lại khi chuyển đổi Wi-Fi / 4G",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = Color(0xFFA0A0AB), lineHeight = 18.sp)
                                )
                            }
                        }
                    }
                }

                if (isMultiColumn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1.15f)) {
                            connectionSlotsContent()
                        }
                        Box(modifier = Modifier.weight(0.85f)) {
                            helperActionsContent()
                        }
                    }
                } else {
                    connectionSlotsContent()
                    Spacer(modifier = Modifier.height(18.dp))
                    helperActionsContent()
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showAddSlotPicker) {
        AddConnectionModeDialog(
            onDismissRequest = { showAddSlotPicker = false },
            onSelectMode = { mode ->
                if (!hasPrimarySlot) {
                    primarySlotMode = mode
                    hasPrimarySlot = true
                } else {
                    val slotId = "slot-${nextSlotIndex++}"
                    extraSlots = extraSlots + AgentConnectionSlot(
                        id = slotId,
                        mode = mode,
                        remoteRoomCode = ""
                    )
                }
                showAddSlotPicker = false
            },
            onScanQrNow = {
                showAddSlotPicker = false
                triggerQrScan()
            }
        )
    }
}
