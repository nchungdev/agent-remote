package app.duinch.agentremote.features.settings.data

import kotlinx.serialization.json.Json
import java.io.File

actual class PlatformSettingsStorage {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val configFile: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".config/agent-remote")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "settings.json")
    }

    actual fun load(): AgentSettingsPreferences {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                json.decodeFromString<AgentSettingsPreferences>(content)
            } else {
                AgentSettingsPreferences()
            }
        } catch (_: Exception) {
            AgentSettingsPreferences()
        }
    }

    actual fun save(prefs: AgentSettingsPreferences) {
        try {
            val text = json.encodeToString(AgentSettingsPreferences.serializer(), prefs)
            configFile.writeText(text)
        } catch (_: Exception) {
        }
    }
}
