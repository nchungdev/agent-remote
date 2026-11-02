package app.duinch.agentremote.features.chat.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType

sealed interface ChatIntent {
    data class Connect(val url: String) : ChatIntent
    data object Disconnect : ChatIntent
    data class SelectAgent(val agentType: AgentType) : ChatIntent
    data class SelectModel(val model: AgentModel) : ChatIntent
    data class SelectConversation(
        val conversationId: String,
        val title: String? = null,
        val agentType: AgentType? = null
    ) : ChatIntent
    data object NewConversation : ChatIntent
    data object RefreshCatalog : ChatIntent
    data class InputChanged(val newText: String) : ChatIntent
    data object SendPrompt : ChatIntent
    data class ApproveTool(val callId: String, val approved: Boolean) : ChatIntent
    data object CancelTask : ChatIntent
    data class UrlChanged(val newUrl: String) : ChatIntent
    data object ClearError : ChatIntent
}
