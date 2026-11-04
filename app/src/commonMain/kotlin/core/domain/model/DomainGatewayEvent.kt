package app.duinch.agentremote.core.domain.model

sealed interface DomainGatewayEvent {
    data class SessionInit(val sessionId: String, val agentType: AgentType, val status: String) : DomainGatewayEvent
    data class AgentStateChanged(val state: AgentState, val details: String? = null) : DomainGatewayEvent
    data class TextChunk(val text: String) : DomainGatewayEvent
    data class ThinkingChunk(val thought: String) : DomainGatewayEvent
    data class ToolCallStarted(val callId: String, val toolName: String, val argsJson: String) : DomainGatewayEvent
    data class ApprovalRequired(val request: ApprovalRequest) : DomainGatewayEvent
    data class ToolResultReceived(val callId: String, val toolName: String, val output: String, val isError: Boolean) : DomainGatewayEvent
    data class MessageCompleted(val messageId: String, val conversationId: String? = null) : DomainGatewayEvent
    data class ErrorOccurred(val message: String) : DomainGatewayEvent
    data class CatalogLoaded(val catalog: EngineCatalog) : DomainGatewayEvent
    data class ConversationHistory(val engine: String, val conversationId: String, val messages: List<ChatMessage>) : DomainGatewayEvent
    data object Pong : DomainGatewayEvent
}
