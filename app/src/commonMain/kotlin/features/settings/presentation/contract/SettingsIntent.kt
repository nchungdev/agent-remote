package app.duinch.agentremote.features.settings.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentType

sealed interface SettingsIntent {
    data class UpdateGatewayHost(val host: String) : SettingsIntent
    data class UpdateAuthToken(val token: String) : SettingsIntent
    data object TestPing : SettingsIntent
    data class SelectDefaultAgent(val agent: AgentType) : SettingsIntent
    data object CheckUpdate : SettingsIntent
    data object DownloadAndInstallUpdate : SettingsIntent
    data object DismissMessage : SettingsIntent
}
