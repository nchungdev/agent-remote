package app.duinch.agentremote.core.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GatewayEventDto(
    val type: String,
    val sessionId: String? = null,
    val agentType: String? = null,
    val status: String? = null,
    val state: String? = null,
    val details: String? = null,
    val text: String? = null,
    val thought: String? = null,
    val callId: String? = null,
    val toolName: String? = null,
    val summary: String? = null,
    val dangerous: Boolean? = null,
    val output: String? = null,
    val isError: Boolean? = null,
    val messageId: String? = null,
    val message: String? = null,
    val timestamp: Long? = null,
    val args: JsonObject? = null,
    val catalog: EngineCatalogDto? = null,
    val engine: String? = null,
    val conversationId: String? = null,
    val messages: List<HistoryMessageDto>? = null,
    val activeModel: String? = null,
    val tokenUsage: TokenUsageDto? = null
)

@Serializable
data class TokenUsageDto(
    val inputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val contextLimit: Int = 1000000
)

@Serializable
data class HistoryMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val thinking: String? = null,
    val timestampMs: Long? = null
)
