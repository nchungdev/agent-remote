package app.duinch.agentremote.core.domain.model

enum class ToolStatus {
    PENDING_APPROVAL,
    RUNNING,
    SUCCESS,
    FAILED,
    REJECTED
}

data class ToolCall(
    val id: String,
    val toolName: String,
    val argsJson: String,
    val summary: String? = null,
    val dangerous: Boolean = false,
    val status: ToolStatus = ToolStatus.RUNNING,
    val result: String? = null
)
