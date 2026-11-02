package app.duinch.agentremote.core.domain.model

data class AgentModel(
    val id: String,
    val displayName: String,
    val description: String = "",
    val efforts: List<String> = emptyList()
)
