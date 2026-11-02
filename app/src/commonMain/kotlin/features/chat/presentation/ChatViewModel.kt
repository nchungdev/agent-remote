package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.model.MessageRole
import app.duinch.agentremote.core.domain.model.ToolCall
import app.duinch.agentremote.core.domain.model.ToolStatus
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.features.chat.presentation.contract.ChatEffect
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent
import app.duinch.agentremote.features.chat.presentation.contract.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val gatewayClient: AgentGatewayClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ChatEffect> = _effects.asSharedFlow()

    private var currentAssistantMsgId: String? = null

    init {
        observeConnection()
        observeEvents()
    }

    private fun observeConnection() {
        scope.launch {
            gatewayClient.connectionState.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
                if (status is ConnectionStatus.Connected) {
                    _uiState.update { it.copy(selectedAgent = status.agentType) }
                    gatewayClient.requestCatalog()
                } else if (status is ConnectionStatus.Error) {
                    _effects.tryEmit(ChatEffect.ShowSnackbar("Lỗi kết nối: ${status.message}"))
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            gatewayClient.incomingEvents.collect { event ->
                handleGatewayEvent(event)
            }
        }
    }

    private fun handleGatewayEvent(event: DomainGatewayEvent) {
        when (event) {
            is DomainGatewayEvent.SessionInit -> {
                _uiState.update { it.copy(selectedAgent = event.agentType) }
                scope.launch { gatewayClient.requestCatalog() }
            }

            is DomainGatewayEvent.CatalogLoaded -> {
                val cat = event.catalog
                val updatedCatalogs = _uiState.value.catalogs + (cat.agentType to cat)
                _uiState.update { current ->
                    val newAvailableModels = if (cat.agentType == current.selectedAgent) {
                        cat.models
                    } else {
                        current.availableModels
                    }
                    val newSelectedModel = if (cat.agentType == current.selectedAgent) {
                        current.selectedModel?.takeIf { sm -> cat.models.any { it.id == sm.id } }
                            ?: cat.models.firstOrNull()
                    } else {
                        current.selectedModel
                    }
                    current.copy(
                        catalogs = updatedCatalogs,
                        availableModels = newAvailableModels,
                        selectedModel = newSelectedModel
                    )
                }
            }

            is DomainGatewayEvent.AgentStateChanged -> {
                _uiState.update { it.copy(agentState = event.state) }
            }

            is DomainGatewayEvent.ThinkingChunk -> {
                appendThinkingChunk(event.thought)
            }

            is DomainGatewayEvent.TextChunk -> {
                appendTextChunk(event.text)
            }

            is DomainGatewayEvent.ToolCallStarted -> {
                addToolCall(event.callId, event.toolName, event.argsJson)
            }

            is DomainGatewayEvent.ApprovalRequired -> {
                _uiState.update { it.copy(pendingApproval = event.request) }
                _effects.tryEmit(ChatEffect.TriggerHapticFeedback)
            }

            is DomainGatewayEvent.ToolResultReceived -> {
                updateToolResult(event.callId, event.output, event.isError)
            }

            is DomainGatewayEvent.MessageCompleted -> {
                finishAssistantMessage()
            }

            is DomainGatewayEvent.ErrorOccurred -> {
                _uiState.update { it.copy(errorMessage = event.message) }
            }

            is DomainGatewayEvent.ConversationHistory -> {
                if (_uiState.value.activeConversationId == event.conversationId) {
                    _uiState.update { it.copy(messages = event.messages) }
                    _effects.tryEmit(ChatEffect.ScrollToBottom)
                }
            }

            DomainGatewayEvent.Pong -> Unit
        }
    }

    fun processIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Connect -> connect(intent.url)
            ChatIntent.Disconnect -> disconnect()
            is ChatIntent.SelectAgent -> selectAgent(intent.agentType)
            is ChatIntent.SelectModel -> selectModel(intent.model)
            is ChatIntent.SelectConversation -> {
                val targetAgent = intent.agentType ?: _uiState.value.selectedAgent
                _uiState.update {
                    it.copy(
                        activeConversationId = intent.conversationId,
                        activeConversationTitle = intent.title ?: intent.conversationId.take(8),
                        selectedAgent = targetAgent,
                        messages = emptyList()
                    )
                }
                scope.launch {
                    gatewayClient.requestHistory(intent.conversationId, targetAgent)
                }
            }
            ChatIntent.NewConversation -> {
                _uiState.update {
                    it.copy(
                        activeConversationId = null,
                        activeConversationTitle = null,
                        messages = emptyList()
                    )
                }
            }
            ChatIntent.RefreshCatalog -> {
                scope.launch { gatewayClient.requestCatalog() }
            }
            is ChatIntent.InputChanged -> _uiState.update { it.copy(inputText = intent.newText) }
            ChatIntent.SendPrompt -> sendPrompt()
            is ChatIntent.ApproveTool -> approveTool(intent.callId, intent.approved)
            ChatIntent.CancelTask -> cancelTask()
            is ChatIntent.UrlChanged -> _uiState.update { it.copy(gatewayUrl = intent.newUrl) }
            ChatIntent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun connect(url: String) {
        _uiState.update { it.copy(gatewayUrl = url) }
        scope.launch {
            gatewayClient.connect(url)
        }
    }

    private fun disconnect() {
        scope.launch {
            gatewayClient.disconnect()
        }
    }

    private fun selectAgent(type: AgentType) {
        scope.launch {
            val models = _uiState.value.catalogs[type]?.models.orEmpty()
            _uiState.update {
                it.copy(
                    selectedAgent = type,
                    availableModels = models,
                    selectedModel = models.firstOrNull()
                )
            }
            gatewayClient.selectAgent(type)
            gatewayClient.requestCatalog()
        }
    }

    private fun selectModel(model: AgentModel) {
        scope.launch {
            _uiState.update { it.copy(selectedModel = model) }
            gatewayClient.selectModel(model.id)
        }
    }

    private fun sendPrompt() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val userMsg = ChatMessage(
            id = "user_${timestamp()}",
            role = MessageRole.USER,
            content = text,
            timestamp = timestamp()
        )

        val assistantId = "assistant_${timestamp()}"
        currentAssistantMsgId = assistantId
        val assistantMsg = ChatMessage(
            id = assistantId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            timestamp = timestamp()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                inputText = "",
                agentState = AgentState.THINKING
            )
        }

        scope.launch {
            val modelId = _uiState.value.selectedModel?.id
            val convId = _uiState.value.activeConversationId
            val res = gatewayClient.sendPrompt(text, modelId, convId)
            if (res is AppResult.Failure) {
                val errMsg = when (val err = res.error) {
                    is DomainError.Network -> err.message
                    is DomainError.Protocol -> err.message
                    is DomainError.Unauthorized -> err.message
                    is DomainError.AgentUnavailable -> "Agent ${err.agentName} không khả dụng"
                    is DomainError.Unknown -> err.message
                }
                _uiState.update { state ->
                    val updated = state.messages.map { msg ->
                        if (msg.id == assistantId) {
                            msg.copy(
                                content = "⚠️ Lỗi gửi lệnh: $errMsg. Vui lòng kiểm tra lại kết nối Gateway.",
                                isStreaming = false
                            )
                        } else msg
                    }
                    state.copy(messages = updated, agentState = AgentState.ERROR)
                }
            }
        }
    }

    private fun approveTool(callId: String, approved: Boolean) {
        _uiState.update { it.copy(pendingApproval = null) }
        scope.launch {
            gatewayClient.approveTool(callId, approved)
        }
    }

    private fun cancelTask() {
        scope.launch {
            gatewayClient.cancelTask()
            _uiState.update { it.copy(agentState = AgentState.IDLE) }
        }
    }

    private fun appendThinkingChunk(thought: String) {
        val currentId = currentAssistantMsgId ?: createAssistantMessage()
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == currentId) {
                    msg.copy(thinking = (msg.thinking ?: "") + thought)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    private fun appendTextChunk(text: String) {
        val currentId = currentAssistantMsgId ?: createAssistantMessage()
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == currentId) {
                    msg.copy(content = msg.content + text)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    private fun addToolCall(callId: String, name: String, args: String) {
        val currentId = currentAssistantMsgId ?: createAssistantMessage()
        val toolCall = ToolCall(id = callId, toolName = name, argsJson = args, status = ToolStatus.RUNNING)
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == currentId) {
                    msg.copy(toolCalls = msg.toolCalls + toolCall)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    private fun updateToolResult(callId: String, output: String, isError: Boolean) {
        val currentId = currentAssistantMsgId ?: return
        val newStatus = if (isError) ToolStatus.FAILED else ToolStatus.SUCCESS
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == currentId) {
                    val updatedTools = msg.toolCalls.map { tc ->
                        if (tc.id == callId) tc.copy(result = output, status = newStatus) else tc
                    }
                    msg.copy(toolCalls = updatedTools)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    private fun finishAssistantMessage() {
        val currentId = currentAssistantMsgId
        currentAssistantMsgId = null
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == currentId) msg.copy(isStreaming = false) else msg
            }
            state.copy(messages = updated, agentState = AgentState.IDLE)
        }
    }

    private fun createAssistantMessage(): String {
        val id = "assistant_${timestamp()}"
        currentAssistantMsgId = id
        val assistantMsg = ChatMessage(
            id = id,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            timestamp = timestamp()
        )
        _uiState.update { it.copy(messages = it.messages + assistantMsg) }
        return id
    }

    private fun timestamp(): Long = app.duinch.agentremote.core.presentation.util.currentTimeMillis()
}
