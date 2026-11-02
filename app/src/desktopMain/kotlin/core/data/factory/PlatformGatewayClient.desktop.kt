package app.duinch.agentremote.core.data.factory

import app.duinch.agentremote.core.data.network.KtorWebSocketClient
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient

actual fun createPlatformAgentGatewayClient(): AgentGatewayClient = KtorWebSocketClient()
