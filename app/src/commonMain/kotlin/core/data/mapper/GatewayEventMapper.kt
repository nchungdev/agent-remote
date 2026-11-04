package app.duinch.agentremote.core.data.mapper

import app.duinch.agentremote.core.data.dto.EngineCatalogDto
import app.duinch.agentremote.core.data.dto.GatewayEventDto
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ApprovalRequest
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import app.duinch.agentremote.core.domain.model.EngineCatalog
import app.duinch.agentremote.core.domain.model.EngineConversation
import app.duinch.agentremote.core.domain.model.EngineProject
import app.duinch.agentremote.core.domain.model.MessageRole

object GatewayEventMapper {

    fun mapToDomain(dto: GatewayEventDto): DomainGatewayEvent? {
        return when (dto.type) {
            "SESSION_INIT" -> DomainGatewayEvent.SessionInit(
                sessionId = dto.sessionId.orEmpty(),
                agentType = AgentType.fromString(dto.agentType.orEmpty()),
                status = dto.status.orEmpty()
            )

            "AGENT_STATE" -> {
                val state = runCatching { AgentState.valueOf(dto.state.orEmpty()) }
                    .getOrDefault(AgentState.IDLE)
                DomainGatewayEvent.AgentStateChanged(state, dto.details)
            }

            "TEXT_CHUNK" -> DomainGatewayEvent.TextChunk(dto.text.orEmpty())

            "THINKING_CHUNK" -> DomainGatewayEvent.ThinkingChunk(dto.thought.orEmpty())

            "TOOL_CALL" -> DomainGatewayEvent.ToolCallStarted(
                callId = dto.callId.orEmpty(),
                toolName = dto.toolName.orEmpty(),
                argsJson = dto.args?.toString().orEmpty()
            )

            "APPROVAL_REQUIRED" -> DomainGatewayEvent.ApprovalRequired(
                ApprovalRequest(
                    callId = dto.callId.orEmpty(),
                    toolName = dto.toolName.orEmpty(),
                    summary = dto.summary ?: "Yêu cầu phê duyệt thực thi lệnh",
                    dangerous = dto.dangerous ?: false
                )
            )

            "TOOL_RESULT" -> DomainGatewayEvent.ToolResultReceived(
                callId = dto.callId.orEmpty(),
                toolName = dto.toolName.orEmpty(),
                output = dto.output.orEmpty(),
                isError = dto.isError ?: false
            )

            "MESSAGE_END" -> DomainGatewayEvent.MessageCompleted(dto.messageId.orEmpty(), dto.conversationId)

            "ERROR" -> DomainGatewayEvent.ErrorOccurred(dto.message ?: "Lỗi không xác định")

            "CATALOG" -> dto.catalog?.let(::mapCatalog)?.let(DomainGatewayEvent::CatalogLoaded)

            "CONVERSATION_HISTORY" -> {
                val msgs = dto.messages.orEmpty().map { m ->
                    ChatMessage(
                        id = m.id,
                        role = if (m.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                        content = m.content,
                        thinking = m.thinking,
                        timestamp = m.timestampMs ?: 0L
                    )
                }
                DomainGatewayEvent.ConversationHistory(
                    engine = dto.engine.orEmpty(),
                    conversationId = dto.conversationId.orEmpty(),
                    messages = msgs
                )
            }

            "PONG" -> DomainGatewayEvent.Pong

            else -> null
        }
    }

    private fun mapCatalog(dto: EngineCatalogDto): EngineCatalog? {
        val agent = AgentType.entries.firstOrNull { it.name == dto.engine } ?: return null
        return EngineCatalog(
            agentType = agent,
            installed = dto.installed,
            models = dto.models.map { AgentModel(it.id, it.label, it.description, it.efforts) },
            projects = dto.projects.map { EngineProject(it.id, it.name, it.folders) },
            conversations = dto.conversations.map {
                EngineConversation(
                    id = it.id,
                    title = it.title,
                    projectId = it.projectId,
                    workspace = it.workspace,
                    section = it.section,
                    pinned = it.pinned,
                    archived = it.archived,
                    running = it.running,
                    updatedAtMs = it.updatedAtMs
                )
            },
            totalConversations = dto.total,
            error = dto.error
        )
    }
}
