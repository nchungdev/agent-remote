package app.duinch.agentremote.features.settings.data

import android.content.Context
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.presentation.util.AppContextProvider
import app.duinch.agentremote.core.security.KeystoreCipher

actual class PlatformSettingsStorage {

    private val prefs
        get() = AppContextProvider.context?.getSharedPreferences("agent_remote_prefs", Context.MODE_PRIVATE)

    actual fun load(): AgentSettingsPreferences {
        val sp = prefs ?: return AgentSettingsPreferences()
        val agentName = sp.getString("default_agent", AgentType.ANTIGRAVITY.name)
        val defaultAgent = AgentType.entries.firstOrNull { it.name == agentName } ?: AgentType.ANTIGRAVITY
        val recentRaw = KeystoreCipher.decrypt(sp.getString("recent_tunnels", "").orEmpty())
        val recents = recentRaw.split("|").map { it.trim() }.filter { it.isNotBlank() }
        val decryptedGatewayUrl = KeystoreCipher.decrypt(sp.getString("last_gateway_url", "").orEmpty())

        return AgentSettingsPreferences(
            defaultAgent = defaultAgent,
            autoReconnect = sp.getBoolean("auto_reconnect", true),
            notifyConnectionChange = sp.getBoolean("notify_connection_change", true),
            notifyAgentApproval = sp.getBoolean("notify_agent_approval", true),
            lastGatewayUrl = decryptedGatewayUrl,
            recentTunnels = recents
        )
    }

    actual fun save(prefs: AgentSettingsPreferences) {
        val sp = this.prefs ?: return
        val recentJoined = prefs.recentTunnels.joinToString("|")
        sp.edit()
            .putString("default_agent", prefs.defaultAgent.name)
            .putBoolean("auto_reconnect", prefs.autoReconnect)
            .putBoolean("notify_connection_change", prefs.notifyConnectionChange)
            .putBoolean("notify_agent_approval", prefs.notifyAgentApproval)
            .putString("last_gateway_url", KeystoreCipher.encrypt(prefs.lastGatewayUrl))
            .putString("recent_tunnels", KeystoreCipher.encrypt(recentJoined))
            .apply()
    }
}
