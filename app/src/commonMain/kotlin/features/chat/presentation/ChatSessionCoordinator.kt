package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent
import app.duinch.agentremote.features.chat.presentation.contract.ChatUiState
import app.duinch.agentremote.features.settings.data.PlatformSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChatSessionCoordinator(
    private val gatewayClient: AgentGatewayClient,
    private val storage: PlatformSettingsStorage,
    private val scope: CoroutineScope,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val cancelCurrentTask: () -> Unit,
    private val getCurrentState: () -> ChatUiState,
    private val timestamp: () -> Long
) {
    fun handleSwitchWriter(target: AgentType) {
        val s = getCurrentState()
        if (s.agentState.isBusy) {
            updateState { it.copy(switchWriterConfirmTarget = target) }
        } else {
            switchWriterDirect(target)
        }
    }

    fun handleConfirmSwitchWriter(target: AgentType, stopCurrent: Boolean) {
        updateState { it.copy(switchWriterConfirmTarget = null) }
        if (stopCurrent) {
            cancelCurrentTask()
        }
        switchWriterDirect(target)
    }

    fun switchWriterDirect(target: AgentType) {
        val current = getCurrentState()
        val pSessionId = current.activeConversationId ?: "ps_${timestamp()}"
        val catalogModels = current.catalogs[target]?.models.orEmpty().ifEmpty { target.defaultModels() }
        val newModel = catalogModels.firstOrNull()
        updateState {
            ChatStateReducer.syncAgentPermissions(it, target).copy(
                selectedAgent = target,
                availableModels = catalogModels,
                selectedModel = newModel,
                isSwitchingWriter = false
            )
        }
        val prefs = storage.load()
        storage.save(prefs.copy(lastEngine = target.name, lastModelId = newModel?.id.orEmpty()))
        scope.launch {
            gatewayClient.selectAgent(target)
            gatewayClient.transferWriter(
                parentSessionId = pSessionId,
                targetAgentId = target.name.lowercase(),
                agentType = target,
                model = newModel?.id,
                expectedRevision = 1L
            )
        }
    }

    fun handleCreateNewSession(intent: ChatIntent.CreateNewSession) {
        val newSessionId = "ps_${timestamp()}"
        val targetAgent = intent.agentType
        val catalogModels = getCurrentState().catalogs[targetAgent]?.models.orEmpty().ifEmpty { targetAgent.defaultModels() }
        val chosenModel = catalogModels.firstOrNull()
        updateState {
            ChatStateReducer.syncAgentPermissions(it, targetAgent).copy(
                isNewSessionDialogOpen = false,
                activeConversationId = newSessionId,
                activeConversationTitle = intent.title,
                activeBranch = intent.baseBranch,
                selectedAgent = targetAgent,
                availableModels = catalogModels,
                selectedModel = chosenModel,
                messages = emptyList(),
                agentState = AgentState.IDLE,
                errorMessage = null
            )
        }
        val prefs = storage.load()
        storage.save(prefs.copy(lastConversationId = newSessionId, lastConversationTitle = intent.title, lastEngine = targetAgent.name))
        scope.launch {
            gatewayClient.selectAgent(targetAgent)
            gatewayClient.initParentSession(
                parentSessionId = newSessionId,
                workspace = getCurrentState().activeWorkspace,
                agentType = targetAgent,
                model = chosenModel?.id
            )
        }
    }
}
