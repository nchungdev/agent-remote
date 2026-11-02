package app.duinch.agentremote.features.settings.data

actual class PlatformSettingsStorage {
    private var cached = AgentSettingsPreferences()

    actual fun load(): AgentSettingsPreferences = cached

    actual fun save(prefs: AgentSettingsPreferences) {
        cached = prefs
    }
}
