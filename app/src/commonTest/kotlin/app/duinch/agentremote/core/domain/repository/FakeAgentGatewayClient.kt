package app.duinch.agentremote.core.domain.repository

import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAgentGatewayClient : AgentGatewayClient {

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<DomainGatewayEvent>(extraBufferCapacity = 64)
    override val incomingEvents: Flow<DomainGatewayEvent> = _incomingEvents.asSharedFlow()

    override var autoReconnectEnabled: Boolean = true

    val sentPrompts = mutableListOf<String>()
    val toolApprovals = mutableListOf<Pair<String, Boolean>>()

    override suspend fun connect(url: String, token: String?): AppResult<Unit, DomainError> {
        _connectionState.value = ConnectionStatus.Connected(url, "fake_session", AgentType.MOCK)
        return AppResult.Success(Unit)
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionStatus.Disconnected
    }

    override suspend fun selectAgent(agentType: AgentType): AppResult<Unit, DomainError> {
        return AppResult.Success(Unit)
    }

    var catalogRequests = 0
    var historyRequests = 0

    override suspend fun requestCatalog(): AppResult<Unit, DomainError> {
        catalogRequests++
        return AppResult.Success(Unit)
    }

    override suspend fun requestHistory(
        conversationId: String,
        agentType: AgentType?,
        limit: Int
    ): AppResult<Unit, DomainError> {
        historyRequests++
        return AppResult.Success(Unit)
    }

    override suspend fun sendPrompt(
        prompt: String,
        model: String?,
        conversationId: String?,
        permissionMode: String?,
        workspace: String?,
        agentType: AgentType?
    ): AppResult<Unit, DomainError> {
        sentPrompts.add(prompt)
        return AppResult.Success(Unit)
    }

    override suspend fun approveTool(callId: String, approved: Boolean): AppResult<Unit, DomainError> {
        toolApprovals.add(callId to approved)
        return AppResult.Success(Unit)
    }

    override suspend fun cancelTask(): AppResult<Unit, DomainError> {
        return AppResult.Success(Unit)
    }

    override suspend fun ping(): AppResult<Unit, DomainError> {
        return AppResult.Success(Unit)
    }

    suspend fun emitEvent(event: DomainGatewayEvent) {
        _incomingEvents.emit(event)
    }
}
