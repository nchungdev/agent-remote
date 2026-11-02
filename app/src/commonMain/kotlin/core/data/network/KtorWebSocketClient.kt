package app.duinch.agentremote.core.data.network

import app.duinch.agentremote.core.data.dto.GatewayActionDto
import app.duinch.agentremote.core.data.dto.GatewayEventDto
import app.duinch.agentremote.core.data.mapper.GatewayEventMapper
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class KtorWebSocketClient(
    private val httpClient: HttpClient = HttpClient {
        install(WebSockets) {
            pingIntervalMillis = 15_000L
        }
    },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : AgentGatewayClient {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionJob: Job? = null
    private var activeSession: DefaultClientWebSocketSession? = null

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<DomainGatewayEvent>(extraBufferCapacity = 64)
    override val incomingEvents: Flow<DomainGatewayEvent> = _incomingEvents.asSharedFlow()

    override suspend fun connect(url: String, token: String?): AppResult<Unit, DomainError> {
        disconnect()
        _connectionState.value = ConnectionStatus.Connecting

        return try {
            val normalizedUrl = when {
                url.startsWith("wss://") || url.startsWith("ws://") -> url
                url.startsWith("https://") -> "wss://${url.removePrefix("https://")}"
                url.startsWith("http://") -> "ws://${url.removePrefix("http://")}"
                url.contains("trycloudflare.com") -> "wss://$url"
                else -> "ws://$url"
            }

            connectionJob = scope.launch {
                var retryDelay = 1000L
                while (isActive) {
                    try {
                        _connectionState.value = ConnectionStatus.Connecting
                        httpClient.webSocket(urlString = normalizedUrl) {
                            activeSession = this
                            retryDelay = 1000L

                            for (frame in incoming) {
                                if (!isActive) break
                                if (frame is Frame.Text) {
                                    handleIncomingFrame(frame.readText(), normalizedUrl)
                                }
                            }
                        }
                        _connectionState.value = ConnectionStatus.Disconnected
                    } catch (e: CancellationException) {
                        _connectionState.value = ConnectionStatus.Disconnected
                        throw e
                    } catch (e: Exception) {
                        println("KtorWebSocketClient session error: ${e.message}")
                        _connectionState.value = ConnectionStatus.Error(e.message ?: "Mất kết nối tới Gateway")
                    } finally {
                        activeSession = null
                    }
                    if (!isActive) break
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(8000L)
                }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            println("KtorWebSocketClient connect error: ${e.message}")
            _connectionState.value = ConnectionStatus.Error(e.message ?: "Không thể kết nối")
            AppResult.Failure(DomainError.Network(e.message ?: "Lỗi kết nối", e))
        }
    }

    private suspend fun handleIncomingFrame(text: String, hostUrl: String) {
        val dto = runCatching { json.decodeFromString<GatewayEventDto>(text) }.getOrNull() ?: return
        val domainEvent = GatewayEventMapper.mapToDomain(dto) ?: return

        if (domainEvent is DomainGatewayEvent.SessionInit) {
            _connectionState.value = ConnectionStatus.Connected(
                host = hostUrl,
                sessionId = domainEvent.sessionId,
                agentType = domainEvent.agentType
            )
        }

        _incomingEvents.emit(domainEvent)
    }

    private suspend fun sendAction(action: GatewayActionDto): AppResult<Unit, DomainError> {
        val session = activeSession
        if (session == null || !session.isActive) {
            return AppResult.Failure(DomainError.Network("Chưa kết nối tới Gateway"))
        }

        return try {
            val payload = json.encodeToString(action)
            session.send(Frame.Text(payload))
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.Network("Lỗi gửi dữ liệu: ${e.message}", e))
        }
    }

    override suspend fun selectAgent(agentType: AgentType): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.selectAgent(agentType.name))
    }

    override suspend fun selectModel(modelId: String): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.selectModel(modelId))
    }

    override suspend fun requestCatalog(): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.listCatalog())
    }

    override suspend fun requestHistory(
        conversationId: String,
        agentType: AgentType?,
        limit: Int
    ): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.getHistory(conversationId, agentType?.name, limit))
    }

    override suspend fun sendPrompt(
        prompt: String,
        model: String?,
        conversationId: String?
    ): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.sendPrompt(prompt, model, conversationId))
    }

    override suspend fun approveTool(callId: String, approved: Boolean): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.approveTool(callId, approved))
    }

    override suspend fun cancelTask(): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.cancelTask())
    }

    override suspend fun ping(): AppResult<Unit, DomainError> {
        return sendAction(GatewayActionDto.ping())
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        try {
            activeSession?.close()
        } catch (_: Exception) {
        }
        activeSession = null
        _connectionState.value = ConnectionStatus.Disconnected
    }
}
