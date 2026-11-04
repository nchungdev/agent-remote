package app.duinch.agentremote.core.data.cache

import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.ConversationTokenUsage
import app.duinch.agentremote.core.domain.model.EngineCatalog
import app.duinch.agentremote.core.domain.model.MessageRole
import app.duinch.agentremote.core.domain.model.ToolCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Two-tier Smart Local Cache (L1 Memory LRU + L2 Persistent Disk).
 * Eliminates blank screens, reduces network roundtrips, and saves bandwidth
 * using the Stale-While-Revalidate pattern.
 */
object ConversationCache {
    private const val MAX_L1_ENTRIES = 50
    private val memoryCache = LinkedHashMap<String, CachedConversation>(MAX_L1_ENTRIES, 0.75f, true)
    private val catalogMemoryCache = LinkedHashMap<AgentType, EngineCatalog>()
    private val diskCache by lazy { PlatformDiskCache() }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class CachedConversation(
        val messages: List<ChatMessage>,
        val activeModel: String? = null,
        val tokenUsage: ConversationTokenUsage? = null,
        val lastUpdatedMs: Long = 0L
    )

    @Serializable
    private data class SerializableToolCall(
        val id: String,
        val toolName: String,
        val argsJson: String = "",
        val summary: String? = null,
        val dangerous: Boolean = false,
        val status: String = "SUCCESS",
        val result: String? = null
    )

    @Serializable
    private data class SerializableMessage(
        val id: String,
        val role: String,
        val content: String,
        val thinking: String? = null,
        val toolCalls: List<SerializableToolCall> = emptyList(),
        val timestamp: Long = 0L
    )

    @Serializable
    private data class SerializableConversation(
        val conversationId: String,
        val messages: List<SerializableMessage>,
        val activeModel: String? = null,
        val tokenUsage: SerializableTokenUsage? = null,
        val savedAtMs: Long = 0L
    )

    @Serializable
    private data class SerializableTokenUsage(
        val inputTokens: Int = 0,
        val cacheReadTokens: Int = 0,
        val outputTokens: Int = 0,
        val totalTokens: Int = 0,
        val contextLimit: Int = 1000000,
        val fiveHourLimitPercent: Int = 14,
        val fiveHourResetTime: String = "53m",
        val weeklyLimitPercent: Int = 51,
        val weeklyResetTime: String = "5d 10h"
    )

    fun get(conversationId: String): CachedConversation? {
        if (conversationId.isBlank()) return null

        // 1. Check L1 Memory Cache
        synchronized(memoryCache) {
            val fromL1 = memoryCache[conversationId]
            if (fromL1 != null) return fromL1
        }

        // 2. Fallback to L2 Disk Cache
        return try {
            val rawJson = diskCache.get("conv_$conversationId") ?: return null
            val dto = json.decodeFromString<SerializableConversation>(rawJson)
            val domainMessages = dto.messages.map { msg ->
                ChatMessage(
                    id = msg.id,
                    role = runCatching { MessageRole.valueOf(msg.role) }.getOrDefault(MessageRole.USER),
                    content = msg.content,
                    thinking = msg.thinking,
                    toolCalls = msg.toolCalls.map { tc ->
                        ToolCall(
                            id = tc.id,
                            toolName = tc.toolName,
                            argsJson = tc.argsJson,
                            summary = tc.summary,
                            dangerous = tc.dangerous,
                            status = runCatching { app.duinch.agentremote.core.domain.model.ToolStatus.valueOf(tc.status) }.getOrDefault(app.duinch.agentremote.core.domain.model.ToolStatus.SUCCESS),
                            result = tc.result
                        )
                    },
                    timestamp = msg.timestamp,
                    isStreaming = false
                )
            }
            val domainToken = dto.tokenUsage?.let { tu ->
                ConversationTokenUsage(
                    inputTokens = tu.inputTokens,
                    cacheReadTokens = tu.cacheReadTokens,
                    outputTokens = tu.outputTokens,
                    totalTokens = tu.totalTokens,
                    contextLimit = tu.contextLimit,
                    fiveHourLimitPercent = tu.fiveHourLimitPercent,
                    fiveHourResetTime = tu.fiveHourResetTime,
                    weeklyLimitPercent = tu.weeklyLimitPercent,
                    weeklyResetTime = tu.weeklyResetTime
                )
            }
            val cached = CachedConversation(
                messages = domainMessages,
                activeModel = dto.activeModel,
                tokenUsage = domainToken,
                lastUpdatedMs = dto.savedAtMs
            )
            // Populate L1
            synchronized(memoryCache) {
                memoryCache[conversationId] = cached
            }
            cached
        } catch (_: Exception) {
            null
        }
    }

    fun put(
        conversationId: String,
        messages: List<ChatMessage>,
        activeModel: String? = null,
        tokenUsage: ConversationTokenUsage? = null
    ) {
        if (conversationId.isBlank()) return
        val now = 0L // or currentTimeMillis if needed
        val entry = CachedConversation(
            messages = messages,
            activeModel = activeModel,
            tokenUsage = tokenUsage,
            lastUpdatedMs = now
        )

        // 1. Update L1 Memory Cache
        synchronized(memoryCache) {
            if (memoryCache.size >= MAX_L1_ENTRIES) {
                val oldest = memoryCache.keys.firstOrNull()
                if (oldest != null) memoryCache.remove(oldest)
            }
            memoryCache[conversationId] = entry
        }

        // 2. Persist to L2 Disk Cache
        try {
            val serializable = SerializableConversation(
                conversationId = conversationId,
                messages = messages.takeLast(100).map { msg ->
                    SerializableMessage(
                        id = msg.id,
                        role = msg.role.name,
                        content = msg.content,
                        thinking = msg.thinking,
                        toolCalls = msg.toolCalls.map { tc ->
                            SerializableToolCall(
                                id = tc.id,
                                toolName = tc.toolName,
                                argsJson = tc.argsJson,
                                summary = tc.summary,
                                dangerous = tc.dangerous,
                                status = tc.status.name,
                                result = tc.result
                            )
                        },
                        timestamp = msg.timestamp
                    )
                },
                activeModel = activeModel,
                tokenUsage = tokenUsage?.let { tu ->
                    SerializableTokenUsage(
                        inputTokens = tu.inputTokens,
                        cacheReadTokens = tu.cacheReadTokens,
                        outputTokens = tu.outputTokens,
                        totalTokens = tu.totalTokens,
                        contextLimit = tu.contextLimit,
                        fiveHourLimitPercent = tu.fiveHourLimitPercent,
                        fiveHourResetTime = tu.fiveHourResetTime,
                        weeklyLimitPercent = tu.weeklyLimitPercent,
                        weeklyResetTime = tu.weeklyResetTime
                    )
                },
                savedAtMs = now
            )
            diskCache.put("conv_$conversationId", json.encodeToString(SerializableConversation.serializer(), serializable))
        } catch (_: Exception) {
        }
    }

    fun putCatalog(agent: AgentType, catalog: EngineCatalog) {
        synchronized(catalogMemoryCache) {
            catalogMemoryCache[agent] = catalog
        }
    }

    fun getCatalog(agent: AgentType): EngineCatalog? {
        return synchronized(catalogMemoryCache) {
            catalogMemoryCache[agent]
        }
    }

    fun clear() {
        synchronized(memoryCache) { memoryCache.clear() }
        synchronized(catalogMemoryCache) { catalogMemoryCache.clear() }
        diskCache.clear()
    }
}
