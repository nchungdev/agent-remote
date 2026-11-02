package app.duinch.agentremote.core.data.webrtc

import android.content.Context
import app.duinch.agentremote.core.data.dto.GatewayActionDto
import app.duinch.agentremote.core.data.dto.GatewayEventDto
import app.duinch.agentremote.core.data.mapper.GatewayEventMapper
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

class WebRtcDataChannelClient(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : AgentGatewayClient {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<DomainGatewayEvent>(extraBufferCapacity = 64)
    override val incomingEvents: Flow<DomainGatewayEvent> = _incomingEvents.asSharedFlow()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private fun ensureFactory(): PeerConnectionFactory {
        val existing = factory
        if (existing != null) return existing
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val created = PeerConnectionFactory.builder().createPeerConnectionFactory()
        factory = created
        return created
    }

    override suspend fun connect(url: String, token: String?): AppResult<Unit, DomainError> = withContext(Dispatchers.IO) {
        disconnect()
        _connectionState.value = ConnectionStatus.Connecting

        val roomCode = url.removePrefix("room:").removePrefix("webrtc://").trim()
        if (roomCode.isBlank()) {
            _connectionState.value = ConnectionStatus.Error("Mã phòng không hợp lệ")
            return@withContext AppResult.Failure(DomainError.Protocol("Mã phòng rỗng"))
        }

        try {
            val pcf = ensureFactory()
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val pc = pcf.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    // ICE Candidate handled via HTTP signaling
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    if (newState == PeerConnection.PeerConnectionState.FAILED) {
                        _connectionState.value = ConnectionStatus.Error("Kết nối P2P thất bại")
                    } else if (newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                        newState == PeerConnection.PeerConnectionState.CLOSED) {
                        _connectionState.value = ConnectionStatus.Disconnected
                    }
                }
            }) ?: run {
                _connectionState.value = ConnectionStatus.Error("Không thể tạo PeerConnection")
                return@withContext AppResult.Failure(DomainError.Network("Không thể khởi tạo WebRTC"))
            }
            peerConnection = pc

            // Tạo DataChannel
            val dcInit = DataChannel.Init().apply { ordered = true }
            val dc = pc.createDataChannel("agent-remote", dcInit)
            dataChannel = dc

            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}

                override fun onStateChange() {
                    when (dc.state()) {
                        DataChannel.State.OPEN -> {
                            _connectionState.value = ConnectionStatus.Connected(
                                host = "P2P WebRTC ($roomCode)",
                                sessionId = "p2p-$roomCode",
                                agentType = AgentType.MOCK
                            )
                        }
                        DataChannel.State.CLOSED -> {
                            _connectionState.value = ConnectionStatus.Disconnected
                        }
                        else -> {}
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, Charsets.UTF_8)
                    handleIncomingFrame(text)
                }
            })

            // Tạo Offer WebRTC
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    pc.setLocalDescription(SimpleSdpObserver(), desc)
                }
            }, MediaConstraints())

            AppResult.Success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionStatus.Error(e.message ?: "Lỗi khởi tạo P2P")
            AppResult.Failure(DomainError.Network(e.message ?: "Lỗi WebRTC", e))
        }
    }

    private fun handleSignal(type: String, payload: String) {
        val pc = peerConnection ?: return
        when (type) {
            "answer" -> {
                val sdp = runCatching { JSONObject(payload).optString("sdp") }.getOrNull().orEmpty()
                if (sdp.isNotBlank()) {
                    pc.setRemoteDescription(
                        SimpleSdpObserver(),
                        SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    )
                }
            }
            "candidate" -> {
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
                val cand = json.optString("candidate")
                val sdpMid = json.optString("sdpMid")
                val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                if (cand.isNotBlank()) {
                    pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, cand))
                }
            }
        }
    }

    private fun handleIncomingFrame(text: String) {
        val dto = runCatching { json.decodeFromString<GatewayEventDto>(text) }.getOrNull() ?: return
        val domainEvent = GatewayEventMapper.mapToDomain(dto) ?: return

        if (domainEvent is DomainGatewayEvent.SessionInit) {
            _connectionState.value = ConnectionStatus.Connected(
                host = "P2P WebRTC",
                sessionId = domainEvent.sessionId,
                agentType = domainEvent.agentType
            )
        }
        scope.launch {
            _incomingEvents.emit(domainEvent)
        }
    }

    private fun sendAction(action: GatewayActionDto): AppResult<Unit, DomainError> {
        val dc = dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            return AppResult.Failure(DomainError.Network("P2P DataChannel chưa sẵn sàng"))
        }
        return try {
            val jsonStr = json.encodeToString(GatewayActionDto.serializer(), action)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(jsonStr.toByteArray(Charsets.UTF_8)), false)
            dc.send(buffer)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.Network("Gửi qua DataChannel thất bại", e))
        }
    }

    override suspend fun disconnect() {
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        _connectionState.value = ConnectionStatus.Disconnected
    }

    override suspend fun selectAgent(agentType: AgentType): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto(action = "SELECT_AGENT", agentType = agentType.name))

    override suspend fun requestCatalog(): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto.listCatalog())

    override suspend fun requestHistory(
        conversationId: String,
        agentType: AgentType?,
        limit: Int
    ): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto.getHistory(conversationId, agentType?.name, limit))

    override suspend fun sendPrompt(
        prompt: String,
        model: String?,
        conversationId: String?
    ): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto.sendPrompt(prompt, model, conversationId))

    override suspend fun approveTool(callId: String, approved: Boolean): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto(action = "APPROVE_TOOL", callId = callId, approved = approved))

    override suspend fun cancelTask(): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto(action = "CANCEL_TASK"))

    override suspend fun ping(): AppResult<Unit, DomainError> =
        sendAction(GatewayActionDto(action = "PING"))
}
