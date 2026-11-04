package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.model.MessageRole
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.presentation.util.PlatformAlertNotifier
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem
import app.duinch.agentremote.features.chat.presentation.contract.ChatEffect
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent
import app.duinch.agentremote.features.chat.presentation.contract.ChatUiState
import app.duinch.agentremote.features.settings.data.PlatformSettingsStorage
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
    private val storage: PlatformSettingsStorage = PlatformSettingsStorage(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val initialPrefs = storage.load()
    private val initialModels = initialPrefs.defaultAgent.defaultModels()
    private val initialPermissions = initialPrefs.defaultAgent.permissionModes()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            gatewayUrl = initialPrefs.lastGatewayUrl,
            selectedAgent = initialPrefs.defaultAgent,
            availableModels = initialModels,
            selectedModel = initialModels.firstOrNull(),
            availablePermissions = initialPermissions,
            selectedPermission = initialPrefs.defaultAgent.defaultPermissionMode()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ChatEffect> = _effects.asSharedFlow()

    private var currentAssistantMsgId: String? = null
    private var previousConnectionStatus: ConnectionStatus? = null

    init {
        observeConnection()
        observeEvents()
    }

    private fun observeConnection() {
        scope.launch {
            gatewayClient.connectionState.collect { status ->
                val prev = previousConnectionStatus
                previousConnectionStatus = status
                _uiState.update { it.copy(connectionStatus = status) }

                val prefs = storage.load()
                if (prefs.notifyConnectionChange && prev != null) {
                    when {
                        status is ConnectionStatus.Connected && prev !is ConnectionStatus.Connected ->
                            PlatformAlertNotifier.notifyConnectionChange(
                                title = "Đã kết nối Agent Gateway",
                                message = "Đã kết nối tới máy chủ ${status.host}"
                            )
                        status is ConnectionStatus.Disconnected &&
                            (prev is ConnectionStatus.Connected || prev is ConnectionStatus.Reconnecting) ->
                            PlatformAlertNotifier.notifyConnectionChange(
                                title = "Đã ngắt kết nối",
                                message = "Đã ngắt kết nối với máy chủ Agent Gateway"
                            )
                        status is ConnectionStatus.Error && prev !is ConnectionStatus.Error ->
                            PlatformAlertNotifier.notifyConnectionChange(
                                title = "Lỗi kết nối máy chủ",
                                message = status.message
                            )
                    }
                }

                if (status is ConnectionStatus.Connected) {
                    _uiState.update { ChatStateReducer.syncAgentPermissions(it, status.agentType) }
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
                _uiState.update { ChatStateReducer.syncAgentPermissions(it, event.agentType) }
                scope.launch { gatewayClient.requestCatalog() }
            }

            is DomainGatewayEvent.CatalogLoaded -> {
                _uiState.update { ChatStateReducer.applyCatalogLoaded(it, event.catalog) }
            }

            is DomainGatewayEvent.AgentStateChanged -> {
                _uiState.update { it.copy(agentState = event.state) }
            }

            is DomainGatewayEvent.ThinkingChunk -> {
                val currentId = currentAssistantMsgId ?: createAssistantMessage()
                _uiState.update { ChatStateReducer.appendThinking(it, currentId, event.thought) }
            }

            is DomainGatewayEvent.TextChunk -> {
                val currentId = currentAssistantMsgId ?: createAssistantMessage()
                _uiState.update { ChatStateReducer.appendText(it, currentId, event.text) }
            }

            is DomainGatewayEvent.ToolCallStarted -> {
                val currentId = currentAssistantMsgId ?: createAssistantMessage()
                _uiState.update { ChatStateReducer.addToolCall(it, currentId, event.callId, event.toolName, event.argsJson) }
            }

            is DomainGatewayEvent.ApprovalRequired -> {
                _uiState.update { it.copy(pendingApproval = event.request) }
                _effects.tryEmit(ChatEffect.TriggerHapticFeedback)
                if (storage.load().notifyAgentApproval) {
                    PlatformAlertNotifier.notifyAgentApproval(
                        toolName = event.request.toolName,
                        description = event.request.summary
                    )
                }
            }

            is DomainGatewayEvent.ToolResultReceived -> {
                val currentId = currentAssistantMsgId ?: return
                _uiState.update { ChatStateReducer.updateToolResult(it, currentId, event.callId, event.output, event.isError) }
            }

            is DomainGatewayEvent.MessageCompleted -> {
                val finishedId = currentAssistantMsgId
                currentAssistantMsgId = null
                _uiState.update { state ->
                    val next = ChatStateReducer.finishStreaming(state, finishedId)
                    next.copy(activeConversationId = next.activeConversationId ?: event.conversationId)
                }
            }

            is DomainGatewayEvent.ErrorOccurred -> {
                val currentId = currentAssistantMsgId
                currentAssistantMsgId = null
                _uiState.update { ChatStateReducer.applyError(it, currentId, event.message) }
                _effects.tryEmit(ChatEffect.ShowSnackbar(event.message))
                _effects.tryEmit(ChatEffect.ScrollToBottom)
            }

            is DomainGatewayEvent.ConversationHistory -> {
                if (_uiState.value.activeConversationId == event.conversationId) {
                    _uiState.update { it.copy(messages = ChatStateReducer.mergeHistory(it, event.messages, currentAssistantMsgId)) }
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
            is ChatIntent.SelectPermission -> _uiState.update { it.copy(selectedPermission = intent.mode) }
            is ChatIntent.SelectConversation -> {
                val targetAgent = intent.agentType ?: _uiState.value.selectedAgent
                currentAssistantMsgId = null
                _uiState.update { current ->
                    ChatStateReducer.syncAgentPermissions(current, targetAgent).copy(
                        selectedAgent = targetAgent,
                        activeConversationId = intent.conversationId,
                        activeConversationTitle = intent.title ?: intent.conversationId.take(8),
                        activeWorkspace = intent.workspace ?: current.activeWorkspace,
                        agentState = AgentState.IDLE,
                        errorMessage = null,
                        messages = emptyList()
                    )
                }
                val prefs = storage.load()
                storage.save(
                    prefs.copy(
                        lastEngine = targetAgent.name,
                        lastConversationId = intent.conversationId,
                        lastConversationTitle = intent.title.orEmpty(),
                        lastWorkspace = intent.workspace.orEmpty()
                    )
                )
                scope.launch {
                    gatewayClient.selectAgent(targetAgent)
                    gatewayClient.requestHistory(intent.conversationId, targetAgent)
                }
            }
            ChatIntent.NewConversation -> {
                currentAssistantMsgId = null
                _uiState.update {
                    it.copy(
                        activeConversationId = null,
                        activeConversationTitle = null,
                        agentState = AgentState.IDLE,
                        errorMessage = null,
                        messages = emptyList()
                    )
                }
                val prefs = storage.load()
                storage.save(prefs.copy(lastConversationId = "", lastConversationTitle = ""))
            }
            is ChatIntent.SelectWorkspace -> {
                _uiState.update { it.copy(activeWorkspace = intent.workspace) }
            }
            ChatIntent.RefreshCatalog -> {
                scope.launch { gatewayClient.requestCatalog() }
            }
            is ChatIntent.InputChanged -> _uiState.update { it.copy(inputText = intent.newText) }
            is ChatIntent.AppendVoiceText -> {
                _uiState.update { ChatStateReducer.appendVoiceText(it, intent.spokenText) }
            }
            is ChatIntent.AttachFile -> attachFile(intent.fileName, intent.bytes)
            is ChatIntent.RemoveAttachment -> {
                _uiState.update { current ->
                    current.copy(attachedFiles = current.attachedFiles.filterNot { it.remotePath == intent.remotePath })
                }
            }
            ChatIntent.SendPrompt -> sendPrompt()
            is ChatIntent.ApproveTool -> approveTool(intent.callId, intent.approved)
            ChatIntent.CancelTask -> cancelTask()
            is ChatIntent.UrlChanged -> _uiState.update { it.copy(gatewayUrl = intent.newUrl) }
            ChatIntent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun connect(url: String) {
        _uiState.update { it.copy(gatewayUrl = url) }
        val prefs = storage.load()
        val updatedRecents = if (url.contains("trycloudflare.com") || url.matches(Regex("""^\d{3}-?\d{3}$"""))) {
            (listOf(url) + prefs.recentTunnels).distinct().take(3)
        } else {
            prefs.recentTunnels
        }
        storage.save(prefs.copy(lastGatewayUrl = url, recentTunnels = updatedRecents))
        scope.launch {
            gatewayClient.connect(url)
        }
    }

    private fun disconnect() {
        val prefs = storage.load()
        storage.save(prefs.copy(lastGatewayUrl = "", lastConversationId = ""))
        scope.launch {
            gatewayClient.disconnect()
        }
    }

    private fun selectAgent(type: AgentType) {
        scope.launch {
            val catalogModels = _uiState.value.catalogs[type]?.models.orEmpty()
            val models = if (catalogModels.isNotEmpty()) catalogModels else type.defaultModels()
            _uiState.update {
                ChatStateReducer.syncAgentPermissions(it, type).copy(
                    availableModels = models,
                    selectedModel = models.firstOrNull(),
                    selectedPermission = type.defaultPermissionMode()
                )
            }
            val prefs = storage.load()
            storage.save(prefs.copy(lastEngine = type.name))
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

    private fun attachFile(fileName: String, bytes: ByteArray) {
        _uiState.update { it.copy(isUploadingAttachment = true) }
        scope.launch {
            when (val res = gatewayClient.uploadFile(fileName, bytes)) {
                is AppResult.Success -> {
                    val item = AttachedFileItem(fileName = fileName, remotePath = res.data)
                    _uiState.update {
                        it.copy(
                            attachedFiles = it.attachedFiles + item,
                            isUploadingAttachment = false
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isUploadingAttachment = false) }
                    _effects.tryEmit(ChatEffect.ShowSnackbar("Không thể tải tệp lên máy chủ"))
                }
            }
        }
    }

    private fun sendPrompt() {
        val rawText = _uiState.value.inputText.trim()
        val attachments = _uiState.value.attachedFiles
        if (rawText.isEmpty() && attachments.isEmpty()) return

        if (rawText.equals("/clear", ignoreCase = true) || rawText.equals("/new", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    activeConversationId = null,
                    activeConversationTitle = null,
                    messages = emptyList(),
                    inputText = "",
                    attachedFiles = emptyList()
                )
            }
            return
        }

        val attachmentSuffix = if (attachments.isNotEmpty()) {
            val paths = attachments.joinToString(", ") { "${it.fileName} (${it.remotePath})" }
            if (rawText.isEmpty()) "Phân tích tệp đính kèm: $paths" else "\n\n[Tệp đính kèm: $paths]"
        } else {
            ""
        }
        val text = rawText + attachmentSuffix

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
                attachedFiles = emptyList(),
                agentState = AgentState.THINKING
            )
        }
        _effects.tryEmit(ChatEffect.ScrollToBottom)

        scope.launch {
            val modelId = _uiState.value.selectedModel?.id
            val convId = _uiState.value.activeConversationId
            val permId = _uiState.value.selectedPermission?.id
            val workspace = _uiState.value.activeWorkspace
            val agent = _uiState.value.selectedAgent
            val res = gatewayClient.sendPrompt(text, modelId, convId, permId, workspace, agent)
            if (res is AppResult.Failure) {
                val errMsg = ChatStateReducer.formatDomainError(res.error)
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
