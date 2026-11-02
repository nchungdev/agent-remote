package app.duinch.agentremote.features.connect.presentation

import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.remotetunnel.ui.ConnectMode
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel

data class AgentConnectionSlot(
    val id: String,
    val mode: ConnectMode = ConnectMode.Remote,
    val serverUrlInput: String = "",
    val remoteRoomCode: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected
)

internal fun buildAgentTelemetry(
    connectionStatus: ConnectionStatus,
    currentHost: String,
    connectedAtEpochMs: Long,
    nowEpochMs: Long
): RemoteTelemetryData {
    val state = when (connectionStatus) {
        is ConnectionStatus.Connected -> RemoteConnectionState.Connected
        is ConnectionStatus.Connecting -> RemoteConnectionState.Connecting
        is ConnectionStatus.Disconnected -> RemoteConnectionState.Disconnected
        else -> RemoteConnectionState.Disconnected
    }

    val channel = when {
        currentHost.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
        currentHost.matches(Regex("""^\d{3}-\d{3}$""")) -> RemoteTransportChannel.WebRtcDirect
        currentHost.isNotBlank() -> RemoteTransportChannel.LanWifi
        else -> RemoteTransportChannel.None
    }

    val roomCode = if (currentHost.matches(Regex("""^\d{3}-\d{3}$"""))) currentHost else ""

    val uptimeSec = if (connectedAtEpochMs > 0L && nowEpochMs > connectedAtEpochMs) {
        (nowEpochMs - connectedAtEpochMs) / 1000
    } else {
        0L
    }

    val ping = when (state) {
        RemoteConnectionState.Connected -> {
            if (channel == RemoteTransportChannel.LanWifi) (1L..3L).random()
            else (30L..45L).random()
        }
        else -> null
    }

    return RemoteTelemetryData(
        state = state,
        channel = channel,
        host = currentHost,
        roomCode = roomCode,
        pingMs = ping,
        uptimeSec = uptimeSec
    )
}
