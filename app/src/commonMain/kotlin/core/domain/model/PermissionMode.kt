package app.duinch.agentremote.core.domain.model

enum class PermissionIconType {
    EYE,
    LOCK,
    SHIELD,
    TURBO
}

data class PermissionMode(
    val id: String,
    val label: String,
    val description: String,
    val iconType: PermissionIconType
)
