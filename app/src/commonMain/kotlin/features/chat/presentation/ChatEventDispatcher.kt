package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.data.cache.ConversationCache
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.presentation.util.PlatformAlertNotifier
import app.duinch.agentremote.features.chat.presentation.contract.ChatEffect
import app.duinch.agentremote.features.chat.presentation.contract.ChatUiState
import app.duinch.agentremote.features.settings.data.PlatformSettingsStorage

class ChatEventDispatcher(
    private val storage: PlatformSettingsStorage,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val emitEffect: (ChatEffect) -> Unit,
    private val requestCatalog: () -> Unit,
    private val getOrCreateAssistantMsgId: () -> String,
    private val getCurrentAssistantMsgId: () -> String?,
    private val clearAssistantMsgId: () -> Unit,
    private val checkAndSendNextQueued: () -> Unit
) {
    fun handleGatewayEvent(event: DomainGatewayEvent, currentState: ChatUiState) {
        when (event) {
            is DomainGatewayEvent.SessionInit -> {
                updateState { ChatStateReducer.syncAgentPermissions(it, event.agentType) }
                requestCatalog()
            }

            is DomainGatewayEvent.CatalogLoaded -> {
                ConversationCache.putCatalog(event.catalog.agentType, event.catalog)
                updateState { ChatStateReducer.applyCatalogLoaded(it, event.catalog) }
            }

            is DomainGatewayEvent.AgentStateChanged -> {
                if (event.state == AgentState.IDLE) {
                    currentState.activeConversationId?.let { id ->
                        ConversationCache.put(id, currentState.messages, currentState.selectedModel?.id, currentState.tokenUsage)
                    }
                }
                updateState { it.copy(agentState = event.state) }
            }

            is DomainGatewayEvent.ThinkingChunk -> {
                val currentId = getOrCreateAssistantMsgId()
                updateState { ChatStateReducer.appendThinking(it, currentId, event.thought) }
            }

            is DomainGatewayEvent.TextChunk -> {
                val currentId = getOrCreateAssistantMsgId()
                updateState { ChatStateReducer.appendText(it, currentId, event.text) }
            }

            is DomainGatewayEvent.ToolCallStarted -> {
                val currentId = getOrCreateAssistantMsgId()
                updateState { ChatStateReducer.addToolCall(it, currentId, event.callId, event.toolName, event.argsJson) }
            }

            is DomainGatewayEvent.ApprovalRequired -> {
                updateState { it.copy(pendingApproval = event.request) }
                emitEffect(ChatEffect.TriggerHapticFeedback)
                if (storage.load().notifyAgentApproval) {
                    PlatformAlertNotifier.notifyAgentApproval(
                        toolName = event.request.toolName,
                        description = event.request.summary
                    )
                }
            }

            is DomainGatewayEvent.ToolResultReceived -> {
                val currentId = getCurrentAssistantMsgId() ?: return
                updateState { ChatStateReducer.updateToolResult(it, currentId, event.callId, event.output, event.isError) }
            }

            is DomainGatewayEvent.MessageCompleted -> {
                val finishedId = getCurrentAssistantMsgId()
                clearAssistantMsgId()
                updateState { state ->
                    val next = ChatStateReducer.finishStreaming(state, finishedId)
                    next.copy(activeConversationId = next.activeConversationId ?: event.conversationId)
                }
                checkAndSendNextQueued()
            }

            is DomainGatewayEvent.ErrorOccurred -> {
                val currentId = getCurrentAssistantMsgId()
                clearAssistantMsgId()
                updateState { ChatStateReducer.applyError(it, currentId, event.message) }
                emitEffect(ChatEffect.ShowSnackbar(event.message))
                emitEffect(ChatEffect.ScrollToBottom)
            }

            is DomainGatewayEvent.ConversationHistory -> {
                if (currentState.activeConversationId == event.conversationId) {
                    updateState { s ->
                        val matched = ChatStateReducer.findMatchingModel(s.availableModels, event.activeModel)
                        val merged = ChatStateReducer.mergeHistory(s, event.messages, getCurrentAssistantMsgId())
                        ConversationCache.put(event.conversationId, merged, event.activeModel, event.tokenUsage)
                        s.copy(
                            messages = merged,
                            tokenUsage = event.tokenUsage ?: s.tokenUsage,
                            selectedModel = matched ?: s.selectedModel
                        )
                    }
                    emitEffect(ChatEffect.ScrollToBottom)
                }
            }

            DomainGatewayEvent.Pong -> Unit

            is DomainGatewayEvent.WriterTransferred -> {
                emitEffect(ChatEffect.ShowSnackbar("Quyền ghi đã chuyển sang ${event.newWriter} (revision ${event.revision})"))
            }

            is DomainGatewayEvent.ConsultBranchResult -> {
                val consultMsgId = getOrCreateAssistantMsgId()
                updateState { state ->
                    val withText = ChatStateReducer.appendText(state, consultMsgId, "💡 **[Hỏi ý kiến ${event.branch.engine.displayName}]**\n${event.branch.response}")
                    ChatStateReducer.finishStreaming(withText, consultMsgId)
                }
                emitEffect(ChatEffect.ScrollToBottom)
            }

            is DomainGatewayEvent.ParentSessionState -> Unit
        }
    }
}
