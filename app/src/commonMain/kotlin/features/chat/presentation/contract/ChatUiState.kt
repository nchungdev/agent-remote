package app.duinch.agentremote.features.chat.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ApprovalRequest
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.ConversationTokenUsage
import app.duinch.agentremote.core.domain.model.EngineCatalog
import app.duinch.agentremote.core.domain.model.PermissionMode

data class AttachedFileItem(
    val fileName: String,
    val remotePath: String
)

data class QueuedMessage(
    val id: String,
    val text: String,
    val attachments: List<AttachedFileItem> = emptyList(),
    val timestamp: Long = 0L
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val agentState: AgentState = AgentState.IDLE,
    val selectedAgent: AgentType = AgentType.ANTIGRAVITY,
    val selectedModel: AgentModel? = null,
    val availableModels: List<AgentModel> = emptyList(),
    val selectedPermission: PermissionMode? = null,
    val availablePermissions: List<PermissionMode> = emptyList(),
    val catalogs: Map<AgentType, EngineCatalog> = emptyMap(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String? = null,
    val activeWorkspace: String? = null,
    val attachedFiles: List<AttachedFileItem> = emptyList(),
    val isUploadingAttachment: Boolean = false,
    val queuedMessages: List<QueuedMessage> = emptyList(),
    val pendingApproval: ApprovalRequest? = null,
    val tokenUsage: ConversationTokenUsage? = null,
    val inputText: String = "",
    val gatewayUrl: String = "",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null
) {
    val canSendPrompt: Boolean
        get() = connectionStatus.isConnected &&
            !isUploadingAttachment &&
            (inputText.isNotBlank() || attachedFiles.isNotEmpty())
}
