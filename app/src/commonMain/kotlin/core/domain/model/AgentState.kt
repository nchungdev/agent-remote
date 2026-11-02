package app.duinch.agentremote.core.domain.model

enum class AgentState {
    IDLE,
    THINKING,
    EXECUTING_TOOL,
    WAITING_APPROVAL,
    ERROR;

    val isBusy: Boolean
        get() = this == THINKING || this == EXECUTING_TOOL || this == WAITING_APPROVAL
}
