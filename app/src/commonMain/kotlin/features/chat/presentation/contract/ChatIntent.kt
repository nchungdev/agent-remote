package app.duinch.agentremote.features.chat.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.PermissionMode

sealed interface ChatIntent {
    data class Connect(val url: String) : ChatIntent
    data object Disconnect : ChatIntent
    data class SelectAgent(val agentType: AgentType) : ChatIntent
    data class SelectModel(val model: AgentModel) : ChatIntent
    data class SelectPermission(val mode: PermissionMode) : ChatIntent
    data class SelectConversation(
        val conversationId: String,
        val title: String? = null,
        val agentType: AgentType? = null,
        val workspace: String? = null
    ) : ChatIntent
    data object NewConversation : ChatIntent
    data class SelectWorkspace(val workspace: String?) : ChatIntent
    data object RefreshCatalog : ChatIntent
    data class InputChanged(val newText: String) : ChatIntent
    data class AppendVoiceText(val spokenText: String) : ChatIntent
    data class AttachFile(val fileName: String, val bytes: ByteArray) : ChatIntent
    data class RemoveAttachment(val remotePath: String) : ChatIntent
    data object SendPrompt : ChatIntent
    data class ApproveTool(val callId: String, val approved: Boolean) : ChatIntent
    data object CancelTask : ChatIntent
    data class UrlChanged(val newUrl: String) : ChatIntent
    data object ClearError : ChatIntent
}
