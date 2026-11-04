package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.EngineCatalog
import app.duinch.agentremote.core.domain.model.MessageRole
import app.duinch.agentremote.core.domain.model.ToolCall
import app.duinch.agentremote.core.domain.model.ToolStatus
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem
import app.duinch.agentremote.features.chat.presentation.contract.ChatUiState
import app.duinch.agentremote.features.chat.presentation.contract.QueuedMessage

internal object ChatStateReducer {

    fun syncAgentPermissions(current: ChatUiState, agentType: AgentType): ChatUiState {
        val perms = agentType.permissionModes()
        val nextPerm = current.selectedPermission?.takeIf { sp -> perms.any { it.id == sp.id } }
            ?: agentType.defaultPermissionMode()
        return current.copy(
            selectedAgent = agentType,
            availablePermissions = perms,
            selectedPermission = nextPerm
        )
    }

    fun applyCatalogLoaded(current: ChatUiState, cat: EngineCatalog): ChatUiState {
        val updatedCatalogs = current.catalogs + (cat.agentType to cat)
        val catModels = if (cat.models.isNotEmpty()) cat.models else cat.agentType.defaultModels()
        val newAvailableModels = if (cat.agentType == current.selectedAgent) {
            catModels
        } else {
            current.availableModels
        }
        val newSelectedModel = if (cat.agentType == current.selectedAgent) {
            current.selectedModel?.takeIf { sm -> catModels.any { it.id == sm.id } }
                ?: catModels.firstOrNull()
        } else {
            current.selectedModel
        }
        return current.copy(
            catalogs = updatedCatalogs,
            availableModels = newAvailableModels,
            selectedModel = newSelectedModel
        )
    }

    fun appendThinking(state: ChatUiState, messageId: String, thought: String): ChatUiState {
        val updated = state.messages.map { msg ->
            if (msg.id == messageId) msg.copy(thinking = (msg.thinking ?: "") + thought) else msg
        }
        return state.copy(messages = updated)
    }

    fun appendText(state: ChatUiState, messageId: String, text: String): ChatUiState {
        val updated = state.messages.map { msg ->
            if (msg.id == messageId) msg.copy(content = msg.content + text) else msg
        }
        return state.copy(messages = updated)
    }

    fun addToolCall(state: ChatUiState, messageId: String, callId: String, name: String, args: String): ChatUiState {
        val toolCall = ToolCall(id = callId, toolName = name, argsJson = args, status = ToolStatus.RUNNING)
        val updated = state.messages.map { msg ->
            if (msg.id == messageId) msg.copy(toolCalls = msg.toolCalls + toolCall) else msg
        }
        return state.copy(messages = updated)
    }

    fun updateToolResult(state: ChatUiState, messageId: String, callId: String, output: String, isError: Boolean): ChatUiState {
        val newStatus = if (isError) ToolStatus.FAILED else ToolStatus.SUCCESS
        val updated = state.messages.map { msg ->
            if (msg.id == messageId) {
                val updatedTools = msg.toolCalls.map { tc ->
                    if (tc.id == callId) tc.copy(result = output, status = newStatus) else tc
                }
                msg.copy(toolCalls = updatedTools)
            } else msg
        }
        return state.copy(messages = updated)
    }

    fun finishStreaming(state: ChatUiState, messageId: String?): ChatUiState {
        val updated = state.messages.map { msg ->
            if (msg.id == messageId) msg.copy(isStreaming = false) else msg
        }
        return state.copy(messages = updated, agentState = AgentState.IDLE)
    }

    fun formatDomainError(err: DomainError): String = when (err) {
        is DomainError.Network -> err.message
        is DomainError.Protocol -> err.message
        is DomainError.Unauthorized -> err.message
        is DomainError.AgentUnavailable -> "Agent ${err.agentName} không khả dụng"
        is DomainError.Unknown -> err.message
    }

    fun appendVoiceText(current: ChatUiState, spoken: String): ChatUiState {
        val trimmed = spoken.trim()
        if (trimmed.isEmpty()) return current
        val next = if (current.inputText.isBlank()) trimmed else "${current.inputText.trimEnd()} $trimmed"
        return current.copy(inputText = next)
    }

    fun applyError(current: ChatUiState, messageId: String?, errMsg: String): ChatUiState {
        val next = if (messageId != null) {
            current.copy(
                agentState = AgentState.ERROR,
                messages = current.messages.map { msg ->
                    if (msg.id == messageId) {
                        val errContent = if (msg.content.isNotBlank()) "${msg.content}\n\n⚠️ $errMsg" else "⚠️ $errMsg"
                        msg.copy(content = errContent, isStreaming = false)
                    } else msg
                }
            )
        } else {
            current.copy(agentState = AgentState.ERROR)
        }
        return next.copy(errorMessage = errMsg)
    }

    fun mergeHistory(current: ChatUiState, history: List<ChatMessage>, activeMsgId: String?): List<ChatMessage> {
        val merged = if (activeMsgId != null) {
            val activeTurn = current.messages.filter { it.isStreaming || it.id == activeMsgId }
            history + activeTurn
        } else {
            history
        }
        return merged.distinctBy { it.id }
    }

    fun queuePrompt(current: ChatUiState, rawText: String, attachments: List<AttachedFileItem>, timestamp: Long): ChatUiState =
        current.copy(
            queuedMessages = current.queuedMessages + QueuedMessage("queue_$timestamp", rawText, attachments, timestamp),
            inputText = "",
            attachedFiles = emptyList()
        )

    fun removeQueued(current: ChatUiState, queueId: String): ChatUiState =
        current.copy(queuedMessages = current.queuedMessages.filterNot { it.id == queueId })

    fun editQueued(current: ChatUiState, queueId: String): ChatUiState {
        val item = current.queuedMessages.firstOrNull { it.id == queueId } ?: return current
        return current.copy(
            queuedMessages = current.queuedMessages.filterNot { it.id == queueId },
            inputText = item.text,
            attachedFiles = item.attachments
        )
    }

    fun findMatchingModel(availableModels: List<AgentModel>, modelName: String?): AgentModel? {
        if (modelName == null) return null
        val low = modelName.lowercase().replace("-", " ")
        return availableModels.firstOrNull { m ->
            val mLow = m.displayName.lowercase().replace("-", " ")
            m.id.equals(modelName, true) || m.displayName.equals(modelName, true) ||
                (low.contains("3.8") && mLow.contains("3.8")) ||
                (low.contains("argon") && mLow.contains("argon")) ||
                (low.contains("opus") && mLow.contains("opus")) ||
                (low.contains("sonnet") && mLow.contains("sonnet")) ||
                (low.contains("gpt") && mLow.contains("gpt"))
        }
    }
}
