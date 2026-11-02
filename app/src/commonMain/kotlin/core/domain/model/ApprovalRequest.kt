package app.duinch.agentremote.core.domain.model

data class ApprovalRequest(
    val callId: String,
    val toolName: String,
    val summary: String,
    val dangerous: Boolean = false
)
