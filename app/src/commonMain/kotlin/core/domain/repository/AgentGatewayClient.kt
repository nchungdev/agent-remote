package app.duinch.agentremote.core.domain.repository

import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AgentGatewayClient {
    val connectionState: StateFlow<ConnectionStatus>
    val incomingEvents: Flow<DomainGatewayEvent>
    var autoReconnectEnabled: Boolean
        get() = true
        set(_) {}

    suspend fun connect(url: String, token: String? = null): AppResult<Unit, DomainError>
    suspend fun disconnect()
    suspend fun selectAgent(agentType: AgentType): AppResult<Unit, DomainError>
    suspend fun selectModel(modelId: String): AppResult<Unit, DomainError> = AppResult.Success(Unit)
    suspend fun requestCatalog(): AppResult<Unit, DomainError>
    suspend fun requestHistory(
        conversationId: String,
        agentType: AgentType? = null,
        limit: Int = 30
    ): AppResult<Unit, DomainError> = AppResult.Success(Unit)
    suspend fun sendPrompt(
        prompt: String,
        model: String? = null,
        conversationId: String? = null,
        permissionMode: String? = null,
        workspace: String? = null
    ): AppResult<Unit, DomainError>
    suspend fun uploadFile(
        fileName: String,
        bytes: ByteArray
    ): AppResult<String, DomainError> = AppResult.Failure(DomainError.Unknown("Chưa hỗ trợ tải tệp trên kênh này"))
    suspend fun approveTool(callId: String, approved: Boolean): AppResult<Unit, DomainError>
    suspend fun cancelTask(): AppResult<Unit, DomainError>
    suspend fun ping(): AppResult<Unit, DomainError>
}
