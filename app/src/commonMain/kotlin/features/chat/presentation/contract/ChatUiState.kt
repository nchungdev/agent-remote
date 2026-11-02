package app.duinch.agentremote.features.chat.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ApprovalRequest
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.EngineCatalog

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val agentState: AgentState = AgentState.IDLE,
    val selectedAgent: AgentType = AgentType.ANTIGRAVITY,
    val selectedModel: AgentModel? = null,
    val availableModels: List<AgentModel> = emptyList(),
    val catalogs: Map<AgentType, EngineCatalog> = emptyMap(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String? = null,
    val pendingApproval: ApprovalRequest? = null,
    val inputText: String = "",
    val gatewayUrl: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null
) {
    val canSendPrompt: Boolean
        get() = connectionStatus.isConnected && !agentState.isBusy && inputText.isNotBlank()
}
