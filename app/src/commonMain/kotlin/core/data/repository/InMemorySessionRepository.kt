package app.duinch.agentremote.core.data.repository

import app.duinch.agentremote.core.domain.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemorySessionRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addMessage(message: ChatMessage) {
        _messages.update { it + message }
    }

    fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
