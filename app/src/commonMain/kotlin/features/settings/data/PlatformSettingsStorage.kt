package app.duinch.agentremote.features.settings.data

import app.duinch.agentremote.core.domain.model.AgentType

data class AgentSettingsPreferences(
    val defaultAgent: AgentType = AgentType.ANTIGRAVITY,
    val autoReconnect: Boolean = true,
    val notifyConnectionChange: Boolean = true,
    val notifyAgentApproval: Boolean = true,
    val lastGatewayUrl: String = "",
    val recentTunnels: List<String> = emptyList()
)

expect class PlatformSettingsStorage() {
    fun load(): AgentSettingsPreferences
    fun save(prefs: AgentSettingsPreferences)
}
