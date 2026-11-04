package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.data.cache.ConversationCache
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
import app.duinch.agentremote.features.chat.presentation.contract.QueuedMessage
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
    private val lastCached = initialPrefs.lastConversationId.takeIf { it.isNotBlank() }?.let {
        ConversationCache.get(it)
    }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            gatewayUrl = initialPrefs.lastGatewayUrl,
            selectedAgent = initialPrefs.defaultAgent,
            availableModels = initialModels,
            selectedModel = initialModels.firstOrNull { it.id == initialPrefs.lastModelId } ?: initialModels.firstOrNull(),
            availablePermissions = initialPermissions,
            selectedPermission = initialPrefs.defaultAgent.defaultPermissionMode(),
            activeConversationId = initialPrefs.lastConversationId.takeIf { it.isNotBlank() },
            activeConversationTitle = initialPrefs.lastConversationTitle.takeIf { it.isNotBlank() },
            activeWorkspace = initialPrefs.lastWorkspace.takeIf { it.isNotBlank() },
            messages = lastCached?.messages ?: emptyList(),
            tokenUsage = lastCached?.tokenUsage
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ChatEffect> = _effects.asSharedFlow()

    private var currentAssistantMsgId: String? = null
    private var previousConnectionStatus: ConnectionStatus? = null

    private val eventDispatcher = ChatEventDispatcher(
        storage = storage,
        updateState = { transform: (ChatUiState) -> ChatUiState -> _uiState.update(transform) },
        emitEffect = { effect: ChatEffect -> _effects.tryEmit(effect) },
        requestCatalog = { scope.launch { gatewayClient.requestCatalog() } },
        getOrCreateAssistantMsgId = { currentAssistantMsgId ?: createAssistantMessage() },
        getCurrentAssistantMsgId = { currentAssistantMsgId },
        clearAssistantMsgId = { currentAssistantMsgId = null },
        checkAndSendNextQueued = { checkAndSendNextQueued() }
    )

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

                if (storage.load().notifyConnectionChange && prev != null) {
                    when {
                        status is ConnectionStatus.Connected && prev !is ConnectionStatus.Connected ->
                            PlatformAlertNotifier.notifyConnectionChange("Đã kết nối Agent Gateway", "Đã kết nối tới máy chủ ${status.host}")
                        status is ConnectionStatus.Disconnected && (prev is ConnectionStatus.Connected || prev is ConnectionStatus.Reconnecting) ->
                            PlatformAlertNotifier.notifyConnectionChange("Đã ngắt kết nối", "Đã ngắt kết nối với máy chủ Agent Gateway")
                        status is ConnectionStatus.Error && prev !is ConnectionStatus.Error ->
                            PlatformAlertNotifier.notifyConnectionChange("Lỗi kết nối máy chủ", status.message)
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
        eventDispatcher.handleGatewayEvent(event, _uiState.value)
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
                val cached = ConversationCache.get(intent.conversationId)
                _uiState.update { current ->
                    ChatStateReducer.syncAgentPermissions(current, targetAgent).copy(
                        selectedAgent = targetAgent,
                        activeConversationId = intent.conversationId,
                        activeConversationTitle = intent.title ?: intent.conversationId.take(8),
                        activeWorkspace = intent.workspace ?: current.activeWorkspace,
                        agentState = AgentState.IDLE,
                        errorMessage = null,
                        messages = cached?.messages ?: emptyList(),
                        tokenUsage = cached?.tokenUsage ?: current.tokenUsage
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
            is ChatIntent.SendQueuedNow -> handleSendQueuedNow(intent.queueId)
            is ChatIntent.EditQueued -> _uiState.update { ChatStateReducer.editQueued(it, intent.queueId) }
            is ChatIntent.RemoveQueued -> _uiState.update { ChatStateReducer.removeQueued(it, intent.queueId) }
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
            val models = catalogModels.ifEmpty { type.defaultModels() }
            val prefs = storage.load()
            val chosen = models.firstOrNull { it.id == prefs.lastModelId } ?: models.firstOrNull()
            _uiState.update {
                ChatStateReducer.syncAgentPermissions(it, type).copy(
                    availableModels = models, selectedModel = chosen, selectedPermission = type.defaultPermissionMode()
                )
            }
            storage.save(prefs.copy(lastEngine = type.name))
            gatewayClient.selectAgent(type)
            gatewayClient.requestCatalog()
        }
    }

    private fun selectModel(model: AgentModel) {
        scope.launch {
            _uiState.update { it.copy(selectedModel = model) }
            storage.save(storage.load().copy(lastModelId = model.id))
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
        val rawText = _uiState.value.inputText
        val attachments = _uiState.value.attachedFiles
        if (rawText.isBlank() && attachments.isEmpty()) return

        val parsed = ChatPromptParser.parse(rawText, attachments, _uiState.value.selectedAgent)
        if (parsed.isClearCommand) {
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

        val isBusy = _uiState.value.agentState.isBusy || _uiState.value.messages.any { it.isStreaming }
        if (isBusy) {
            _uiState.update { ChatStateReducer.queuePrompt(it, rawText.trim(), attachments, timestamp()) }
            return
        }

        sendPromptDirect(rawText, attachments)
    }

    private fun sendPromptDirect(rawText: String, attachments: List<AttachedFileItem>) {
        val parsed = ChatPromptParser.parse(rawText, attachments, _uiState.value.selectedAgent)
        val text = parsed.formattedText

        val userMsg = ChatMessage(id = "user_${timestamp()}", role = MessageRole.USER, content = text, timestamp = timestamp())
        val assistantId = "assistant_${timestamp()}"
        currentAssistantMsgId = assistantId

        if (parsed.consultTarget != null && parsed.consultQuery != null) {
            val assistantMsg = ChatMessage(
                id = assistantId,
                role = MessageRole.ASSISTANT,
                content = "🔍 Đang hỏi ý kiến ${parsed.consultTarget.displayName}...",
                isStreaming = true,
                timestamp = timestamp()
            )
            _uiState.update {
                it.copy(messages = it.messages + userMsg + assistantMsg, inputText = "", attachedFiles = emptyList(), agentState = AgentState.THINKING)
            }
            _effects.tryEmit(ChatEffect.ScrollToBottom)

            scope.launch {
                val s = _uiState.value
                val pSessionId = s.activeConversationId ?: "ps_${timestamp()}"
                gatewayClient.consultBranch(pSessionId, parsed.consultTarget, parsed.consultQuery, s.selectedModel?.id)
            }
            return
        }

        val assistantMsg = ChatMessage(id = assistantId, role = MessageRole.ASSISTANT, content = "", isStreaming = true, timestamp = timestamp())
        _uiState.update {
            it.copy(messages = it.messages + userMsg + assistantMsg, inputText = "", attachedFiles = emptyList(), agentState = AgentState.THINKING)
        }
        _effects.tryEmit(ChatEffect.ScrollToBottom)

        scope.launch {
            val s = _uiState.value
            val res = gatewayClient.sendPrompt(text, s.selectedModel?.id, s.activeConversationId, s.selectedPermission?.id, s.activeWorkspace, s.selectedAgent)
            if (res is AppResult.Failure) {
                val errMsg = ChatStateReducer.formatDomainError(res.error)
                _uiState.update { state ->
                    val updated = state.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(content = "⚠️ Lỗi gửi lệnh: $errMsg", isStreaming = false) else msg
                    }
                    state.copy(messages = updated, agentState = AgentState.ERROR)
                }
            }
        }
    }

    private fun checkAndSendNextQueued() {
        val queue = _uiState.value.queuedMessages
        if (queue.isNotEmpty()) {
            val next = queue.first()
            _uiState.update { it.copy(queuedMessages = it.queuedMessages.drop(1)) }
            sendPromptDirect(next.text, next.attachments)
        }
    }

    private fun handleSendQueuedNow(queueId: String) {
        val item = _uiState.value.queuedMessages.firstOrNull { it.id == queueId } ?: return
        _uiState.update { ChatStateReducer.removeQueued(it, queueId) }
        sendPromptDirect(item.text, item.attachments)
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
        val msg = ChatMessage(id = id, role = MessageRole.ASSISTANT, content = "", isStreaming = true, timestamp = timestamp())
        _uiState.update { it.copy(messages = it.messages + msg) }
        return id
    }

    private fun timestamp(): Long = app.duinch.agentremote.core.presentation.util.currentTimeMillis()
}
