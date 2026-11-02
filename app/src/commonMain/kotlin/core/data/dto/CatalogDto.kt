package app.duinch.agentremote.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelDto(
    val id: String,
    val label: String,
    val description: String = "",
    val efforts: List<String> = emptyList()
)

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val folders: List<String> = emptyList()
)

@Serializable
data class ConversationDto(
    val id: String,
    val title: String,
    val projectId: String? = null,
    val workspace: String? = null,
    val section: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val running: Boolean = false,
    val updatedAtMs: Long = 0
)

@Serializable
data class EngineCatalogDto(
    val engine: String,
    val installed: Boolean = false,
    val models: List<ModelDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    val conversations: List<ConversationDto> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)
