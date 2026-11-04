package app.duinch.agentremote.features.settings.data

import app.duinch.agentremote.core.domain.model.AgentType
import kotlinx.serialization.Serializable

@Serializable
data class AgentSettingsPreferences(
    val defaultAgent: AgentType = AgentType.ANTIGRAVITY,
    val autoReconnect: Boolean = true,
    val notifyConnectionChange: Boolean = true,
    val notifyAgentApproval: Boolean = true,
    val lastGatewayUrl: String = "",
    val recentTunnels: List<String> = emptyList(),
    val lastEngine: String = "ANTIGRAVITY",
    val lastConversationId: String = "",
    val lastConversationTitle: String = "",
    val lastWorkspace: String = ""
)

expect class PlatformSettingsStorage() {
    fun load(): AgentSettingsPreferences
    fun save(prefs: AgentSettingsPreferences)
}
