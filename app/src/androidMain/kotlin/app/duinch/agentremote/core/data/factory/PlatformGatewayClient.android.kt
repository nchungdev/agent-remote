package app.duinch.agentremote.core.data.factory

import app.duinch.agentremote.core.data.network.KtorWebSocketClient
import app.duinch.agentremote.core.data.webrtc.AndroidCompositeGatewayClient
import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.presentation.util.AppContextProvider

actual fun createPlatformAgentGatewayClient(): AgentGatewayClient {
    val ctx = AppContextProvider.context
    return if (ctx != null) {
        AndroidCompositeGatewayClient(ctx)
    } else {
        KtorWebSocketClient()
    }
}
