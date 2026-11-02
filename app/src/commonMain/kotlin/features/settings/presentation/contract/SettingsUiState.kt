package app.duinch.agentremote.features.settings.presentation.contract

import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.features.updater.domain.model.UpdateStatus

data class SettingsUiState(
    val gatewayHost: String = "",
    val authToken: String = "",
    val pingMs: Long? = null,
    val isPinging: Boolean = false,
    val defaultAgent: AgentType = AgentType.ANTIGRAVITY,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val currentVersionName: String = "0.1.2",
    val currentVersionCode: Int = 1,
    val githubRepoOwner: String = "nchungdev",
    val githubRepoName: String = "agent-remote",
    val message: String? = null
)
