package app.duinch.agentremote.features.chat.presentation.components

import app.duinch.agentremote.core.domain.model.EngineConversation
import app.duinch.agentremote.core.domain.model.EngineProject

internal data class ProjectGroup(
    val id: String,
    val name: String,
    val conversations: List<EngineConversation>
)

internal fun groupConversationsByProject(
    projects: List<EngineProject>,
    conversations: List<EngineConversation>,
    activeConversationId: String? = null
): List<ProjectGroup> {
    val assignedConvIds = mutableSetOf<String>()
    val projectMap = mutableMapOf<String, MutableList<EngineConversation>>()

    // Pass 1: match by projectId
    for (conv in conversations) {
        val pid = conv.projectId
        if (!pid.isNullOrBlank() && projects.any { it.id == pid }) {
            projectMap.getOrPut(pid) { mutableListOf() }.add(conv)
            assignedConvIds.add(conv.id)
        }
    }

    // Pass 2: match unassigned by workspace path or folder name
    for (conv in conversations) {
        if (conv.id in assignedConvIds) continue
        val ws = conv.workspace?.trimEnd('/') ?: ""
        val folderName = ws.substringAfterLast('/')

        val matched = projects.firstOrNull { proj ->
            proj.name.equals(folderName, ignoreCase = true) ||
            proj.folders.any { folder ->
                folder.trimEnd('/').substringAfterLast('/').equals(folderName, ignoreCase = true) ||
                (folder.isNotBlank() && ws.startsWith(folder))
            }
        }

        if (matched != null) {
            projectMap.getOrPut(matched.id) { mutableListOf() }.add(conv)
            assignedConvIds.add(conv.id)
        }
    }

    val result = mutableListOf<ProjectGroup>()

    // Add all defined projects (preserving empty ones)
    for (proj in projects) {
        val convs = projectMap[proj.id].orEmpty().sortedWith(
            compareByDescending<EngineConversation> { it.running }
                .thenByDescending { it.updatedAtMs }
        )
        result.add(ProjectGroup(id = proj.id, name = proj.name, conversations = convs))
    }

    // Pass 3: remaining conversations not mapped to any known project: group by workspace folder name
    val remaining = conversations.filter { it.id !in assignedConvIds }
    if (remaining.isNotEmpty()) {
        val byFolder = remaining.groupBy { conv ->
            conv.workspace?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Khác"
        }
        for ((folder, convs) in byFolder) {
            val existing = result.indexOfFirst { it.name.equals(folder, ignoreCase = true) }
            if (existing >= 0) {
                val current = result[existing]
                val merged = (current.conversations + convs).distinctBy { it.id }.sortedWith(
                    compareByDescending<EngineConversation> { it.running }
                        .thenByDescending { it.updatedAtMs }
                )
                result[existing] = current.copy(conversations = merged)
            } else {
                result.add(
                    ProjectGroup(
                        id = "extra_$folder",
                        name = folder,
                        conversations = convs.sortedWith(
                            compareByDescending<EngineConversation> { it.running }
                                .thenByDescending { it.updatedAtMs }
                        )
                    )
                )
            }
        }
    }

    // Sort projects:
    // 1. Project containing active conversation or running sessions at the TOP
    // 2. Projects with the most recently active conversations (updatedAtMs)
    // 3. Empty projects follow, preserving catalog order
    return result.sortedWith(
        compareByDescending<ProjectGroup> { group ->
            group.conversations.any { it.running || it.id == activeConversationId }
        }.thenByDescending { group ->
            group.conversations.maxOfOrNull { it.updatedAtMs } ?: 0L
        }
    )
}
