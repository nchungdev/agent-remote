package app.duinch.agentremote.core.domain.model

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val host: String, val sessionId: String, val agentType: AgentType) : ConnectionStatus
    data class Reconnecting(val attempt: Int) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus

    val isConnected: Boolean
        get() = this is Connected
}
