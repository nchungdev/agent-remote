package app.duinch.agentremote.core.domain.model

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val timestamp: Long = 0L,
    val isStreaming: Boolean = false
) {
    val hasThinking: Boolean
        get() = !thinking.isNullOrBlank()

    val hasToolCalls: Boolean
        get() = toolCalls.isNotEmpty()
}
