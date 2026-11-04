package app.duinch.agentremote.core.data.webrtc

import android.content.Context
import app.duinch.agentremote.core.data.network.KtorWebSocketClient
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

class AndroidCompositeGatewayClient(
    private val context: Context
) : AgentGatewayClient {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeClient: AgentGatewayClient = KtorWebSocketClient()
    private var observeJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<DomainGatewayEvent>(extraBufferCapacity = 64)
    override val incomingEvents: Flow<DomainGatewayEvent> = _incomingEvents.asSharedFlow()

    override var autoReconnectEnabled: Boolean = true
        set(value) {
            field = value
            activeClient.autoReconnectEnabled = value
        }

    override suspend fun connect(url: String, token: String?): AppResult<Unit, DomainError> {
        disconnect()

        val selectedClient: AgentGatewayClient = if (url.startsWith("webrtc://") || url.startsWith("p2p://")) {
            WebRtcDataChannelClient(context)
        } else {
            KtorWebSocketClient()
        }
        selectedClient.autoReconnectEnabled = autoReconnectEnabled
        activeClient = selectedClient

        observeJob?.cancel()
        observeJob = scope.launch {
            launch {
                selectedClient.connectionState.collect { status ->
                    _connectionState.value = status
                }
            }
            launch {
                selectedClient.incomingEvents.collect { event ->
                    _incomingEvents.emit(event)
                }
            }
        }

        return selectedClient.connect(url, token)
    }

    override suspend fun disconnect() {
        observeJob?.cancel()
        observeJob = null
        activeClient.disconnect()
        _connectionState.value = ConnectionStatus.Disconnected
    }

    override suspend fun selectAgent(agentType: AgentType): AppResult<Unit, DomainError> =
        activeClient.selectAgent(agentType)

    override suspend fun requestCatalog(): AppResult<Unit, DomainError> =
        activeClient.requestCatalog()

    override suspend fun requestHistory(
        conversationId: String,
        agentType: AgentType?,
        limit: Int
    ): AppResult<Unit, DomainError> =
        activeClient.requestHistory(conversationId, agentType, limit)

    override suspend fun sendPrompt(
        prompt: String,
        model: String?,
        conversationId: String?,
        permissionMode: String?,
        workspace: String?
    ): AppResult<Unit, DomainError> =
        activeClient.sendPrompt(prompt, model, conversationId, permissionMode, workspace)

    override suspend fun uploadFile(
        fileName: String,
        bytes: ByteArray
    ): AppResult<String, DomainError> =
        activeClient.uploadFile(fileName, bytes)

    override suspend fun approveTool(callId: String, approved: Boolean): AppResult<Unit, DomainError> =
        activeClient.approveTool(callId, approved)

    override suspend fun cancelTask(): AppResult<Unit, DomainError> =
        activeClient.cancelTask()

    override suspend fun ping(): AppResult<Unit, DomainError> =
        activeClient.ping()
}
