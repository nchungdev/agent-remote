package app.duinch.agentremote.core.domain.model

data class EngineProject(
    val id: String,
    val name: String,
    val folders: List<String>
)

data class EngineConversation(
    val id: String,
    val title: String,
    val projectId: String?,
    val workspace: String?,
    val section: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val running: Boolean,
    val updatedAtMs: Long
)

/** Everything one engine reports about itself, as read by the server from the engine's own store. */
data class EngineCatalog(
    val agentType: AgentType,
    val installed: Boolean,
    val models: List<AgentModel>,
    val projects: List<EngineProject>,
    val conversations: List<EngineConversation>,
    val totalConversations: Int,
    val error: String?
)
