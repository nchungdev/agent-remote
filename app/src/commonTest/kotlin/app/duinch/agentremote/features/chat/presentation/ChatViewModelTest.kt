package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.ApprovalRequest
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.repository.FakeAgentGatewayClient
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeClient: FakeAgentGatewayClient
    private lateinit var viewModel: ChatViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClient = FakeAgentGatewayClient()
        viewModel = ChatViewModel(fakeClient, scope = kotlinx.coroutines.CoroutineScope(testDispatcher))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testConnectChangesStatusToConnected() = runTest(testDispatcher) {
        viewModel.processIntent(ChatIntent.Connect("127.0.0.1:8765"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.connectionStatus.isConnected)
    }

    @Test
    fun testSendPromptAppendsUserMessage() = runTest(testDispatcher) {
        viewModel.processIntent(ChatIntent.Connect("127.0.0.1:8765"))
        advanceUntilIdle()

        viewModel.processIntent(ChatIntent.InputChanged("Run build"))
        viewModel.processIntent(ChatIntent.SendPrompt)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Run build", viewModel.uiState.value.messages.first().content)
        assertEquals(listOf("Run build"), fakeClient.sentPrompts)
        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun testStreamingThinkingAndTextChunks() = runTest(testDispatcher) {
        fakeClient.emitEvent(DomainGatewayEvent.AgentStateChanged(AgentState.THINKING))
        fakeClient.emitEvent(DomainGatewayEvent.ThinkingChunk("Step 1...\n"))
        fakeClient.emitEvent(DomainGatewayEvent.TextChunk("Hello world"))
        fakeClient.emitEvent(DomainGatewayEvent.MessageCompleted("msg_1"))
        advanceUntilIdle()

        val assistantMsg = viewModel.uiState.value.messages.firstOrNull()
        assertNotNull(assistantMsg)
        assertEquals("Step 1...\n", assistantMsg.thinking)
        assertEquals("Hello world", assistantMsg.content)
    }

    @Test
    fun testApprovalWorkflow() = runTest(testDispatcher) {
        val request = ApprovalRequest(
            callId = "call_99",
            toolName = "run_command",
            summary = "Execute git push"
        )
        fakeClient.emitEvent(DomainGatewayEvent.ApprovalRequired(request))
        advanceUntilIdle()

        assertEquals(request, viewModel.uiState.value.pendingApproval)

        // User approves
        viewModel.processIntent(ChatIntent.ApproveTool("call_99", true))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingApproval)
        assertEquals(listOf("call_99" to true), fakeClient.toolApprovals)
    }
}
