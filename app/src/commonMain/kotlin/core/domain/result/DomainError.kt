package app.duinch.agentremote.core.domain.result

sealed interface DomainError {
    data class Network(val message: String, val cause: Throwable? = null) : DomainError
    data class Protocol(val message: String) : DomainError
    data class Unauthorized(val message: String) : DomainError
    data class AgentUnavailable(val agentName: String) : DomainError
    data class Unknown(val message: String, val cause: Throwable? = null) : DomainError
}
