package app.duinch.agentremote.features.connect.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
    var primarySlotMode by remember {
        mutableStateOf(
            if (currentHost.contains("trycloudflare.com") || currentHost.matches(Regex("""^\d{3}-?\d{3}$"""))) ConnectMode.Remote else ConnectMode.Lan
        )
    }
    var extraSlots by remember { mutableStateOf(emptyList<AgentConnectionSlot>()) }
    var nextSlotIndex by remember { mutableStateOf(2) }
    var showAddSlotPicker by remember { mutableStateOf(false) }
    var recentTunnels by remember {
        mutableStateOf(
            PlatformSettingsStorage().load().recentTunnels.ifEmpty {
                if (currentHost.contains("trycloudflare.com")) listOf(currentHost) else emptyList()
            }
        )
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
            currentHost.contains(":") && !currentHost.contains("trycloudflare.com") -> currentHost
            currentHost.isNotBlank() && !currentHost.contains("trycloudflare.com") && !currentHost.matches(Regex("""^\d{3}-?\d{3}$""")) -> currentHost
            else -> ""
        }
    }

    val initialRemote = remember(currentHost) {
        if (currentHost.contains("trycloudflare.com") || currentHost.matches(Regex("""^\d{3}-?\d{3}$"""))) currentHost else ""
    }

    val triggerQrScan = {
        qrScanner.startScan { raw ->
            val parsed = RemoteQrPayloadParser.parse(raw)
            val target = parsed?.lan?.takeIf { it.isNotBlank() }
                ?: parsed?.url?.takeIf { it.isNotBlank() }
                ?: parsed?.roomCode?.takeIf { it.isNotBlank() }
                ?: raw.trim()
            if (target.isNotBlank()) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Unified Telemetry Ribbon (Bento Bar) - chỉ hiển thị khi đang kết nối hoặc đã kết nối
                if (telemetry.state != RemoteConnectionState.Disconnected) {
                    ConnectionTelemetryBar(
                        telemetry = telemetry,
                        onTriggerConnect = triggerQrScan
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val showPrimary = hasPrimarySlot || telemetry.state != RemoteConnectionState.Disconnected

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
                        onClose = {
                            onDisconnect()
                            hasPrimarySlot = false
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
                                if (target.contains("trycloudflare.com") || target.matches(Regex("""^\d{3}-?\d{3}$"""))) {
                                    recentTunnels = (listOf(target) + recentTunnels).distinct().take(3)
                                }
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
                        onClose = { extraSlots = extraSlots.filterNot { it.id == slot.id } },
                        onConnect = { lan, remote ->
                            val target = if (lan.isNotBlank()) lan else remote
                            if (target.isNotBlank()) {
                                if (target.contains("trycloudflare.com") || target.matches(Regex("""^\d{3}-?\d{3}$"""))) {
                                    recentTunnels = (listOf(target) + recentTunnels).distinct().take(3)
                                }
                                onConnect(target)
                            }
                        },
                        onDisconnect = onDisconnect,
                        onScanQr = triggerQrScan
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Nút Thêm máy chủ kết nối mới
                OutlinedButton(
                    onClick = { showAddSlotPicker = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm máy chủ kết nối", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
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
