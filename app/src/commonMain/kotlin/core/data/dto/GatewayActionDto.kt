package app.duinch.agentremote.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GatewayActionDto(
    val action: String,
    val agentType: String? = null,
    val model: String? = null,
    val conversationId: String? = null,
    val parentSessionId: String? = null,
    val targetAgentId: String? = null,
    val expectedRevision: Long? = null,
    val query: String? = null,
    val permissionMode: String? = null,
    val workspace: String? = null,
    val prompt: String? = null,
    val callId: String? = null,
    val approved: Boolean? = null,
    val limit: Int? = null,
    val token: String? = null,
    val clientVersion: String? = null
) {
    companion object {
        fun ping() = GatewayActionDto(action = "PING")

        fun initParentSession(
            parentSessionId: String? = null,
            workspace: String? = null,
            agentType: String? = null,
            model: String? = null
        ) = GatewayActionDto(
            action = "PARENT_SESSION_INIT",
            parentSessionId = parentSessionId,
            workspace = workspace,
            agentType = agentType,
            model = model
        )

        fun transferWriter(
            parentSessionId: String,
            targetAgentId: String,
            agentType: String,
            model: String? = null,
            expectedRevision: Long
        ) = GatewayActionDto(
            action = "TRANSFER_WRITER",
            parentSessionId = parentSessionId,
            targetAgentId = targetAgentId,
            agentType = agentType,
            model = model,
            expectedRevision = expectedRevision
        )

        fun consultBranch(
            parentSessionId: String,
            agentType: String,
            query: String,
            model: String? = null
        ) = GatewayActionDto(
            action = "CONSULT_BRANCH",
            parentSessionId = parentSessionId,
            agentType = agentType,
            query = query,
            model = model
        )

        fun selectAgent(type: String, model: String? = null) =
            GatewayActionDto(action = "SELECT_AGENT", agentType = type, model = model)

        fun selectModel(modelId: String) =
            GatewayActionDto(action = "SELECT_MODEL", model = modelId)

        fun listCatalog() = GatewayActionDto(action = "LIST_CATALOG")

        fun getHistory(conversationId: String, agentType: String? = null, limit: Int? = null) =
            GatewayActionDto(
                action = "GET_HISTORY",
                conversationId = conversationId,
                agentType = agentType,
                limit = limit
            )

        fun sendPrompt(
            promptText: String,
            model: String? = null,
            conversationId: String? = null,
            permissionMode: String? = null,
            workspace: String? = null,
            agentType: String? = null
        ) = GatewayActionDto(
            action = "SEND_PROMPT",
            prompt = promptText,
            model = model,
            conversationId = conversationId,
            permissionMode = permissionMode,
            workspace = workspace,
            agentType = agentType
        )

        fun approveTool(callId: String, isApproved: Boolean) =
            GatewayActionDto(action = "APPROVE_TOOL", callId = callId, approved = isApproved)

        fun cancelTask() = GatewayActionDto(action = "CANCEL_TASK")
    }
}
