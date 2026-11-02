package app.duinch.agentremote.features.settings.presentation

import app.duinch.agentremote.core.domain.repository.AgentGatewayClient
import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.core.presentation.util.currentTimeMillis
import app.duinch.agentremote.features.settings.presentation.contract.SettingsIntent
import app.duinch.agentremote.features.settings.presentation.contract.SettingsUiState
import app.duinch.agentremote.features.updater.domain.model.AppRelease
import app.duinch.agentremote.features.updater.domain.model.UpdateStatus
import app.duinch.agentremote.features.updater.domain.repository.AppUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val updateRepository: AppUpdateRepository,
    private val gatewayClient: AgentGatewayClient? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            currentVersionName = updateRepository.currentVersionName,
            currentVersionCode = updateRepository.currentVersionCode
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun processIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateGatewayHost -> _uiState.update { it.copy(gatewayHost = intent.host) }
            is SettingsIntent.UpdateAuthToken -> _uiState.update { it.copy(authToken = intent.token) }
            SettingsIntent.TestPing -> testPing()
            is SettingsIntent.SelectDefaultAgent -> _uiState.update { it.copy(defaultAgent = intent.agent) }
            SettingsIntent.CheckUpdate -> checkUpdate()
            SettingsIntent.DownloadAndInstallUpdate -> downloadAndInstallUpdate()
            SettingsIntent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun testPing() {
        val client = gatewayClient ?: return
        scope.launch {
            _uiState.update { it.copy(isPinging = true, pingMs = null) }
            val t0 = currentTimeMillis()
            val result = client.ping()
            val latency = currentTimeMillis() - t0
            _uiState.update {
                it.copy(
                    isPinging = false,
                    pingMs = if (result.isSuccess) latency else null,
                    message = if (!result.isSuccess) "Không nhận được phản hồi ping" else null
                )
            }
        }
    }

    private fun checkUpdate() {
        scope.launch {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Checking) }
            val state = _uiState.value
            val result = updateRepository.checkUpdate(state.githubRepoOwner, state.githubRepoName)

            when (result) {
                is AppResult.Success -> {
                    val release = result.data
                    if (release == null) {
                        _uiState.update {
                            it.copy(
                                updateStatus = UpdateStatus.UpToDate("v${it.currentVersionName} (Chưa có bản release trên GitHub)")
                            )
                        }
                    } else if (release.isNewerThanCurrent) {
                        _uiState.update { it.copy(updateStatus = UpdateStatus.Available(release)) }
                    } else {
                        _uiState.update {
                            it.copy(updateStatus = UpdateStatus.UpToDate("v${release.versionName}"))
                        }
                    }
                }

                is AppResult.Failure -> {
                    val errorMsg = when (val err = result.error) {
                        is DomainError.Network -> err.message
                        else -> "Lỗi kiểm tra cập nhật"
                    }
                    _uiState.update { it.copy(updateStatus = UpdateStatus.Error(errorMsg)) }
                }
            }
        }
    }

    private fun downloadAndInstallUpdate() {
        val currentStatus = _uiState.value.updateStatus
        if (currentStatus !is UpdateStatus.Available) return
        val release = currentStatus.release
        val url = release.downloadUrl ?: return

        scope.launch {
            _uiState.update {
                it.copy(updateStatus = UpdateStatus.Downloading(0f, 0L, 0L))
            }

            val downloadResult = updateRepository.downloadApk(url) { progress, downloaded, total ->
                _uiState.update {
                    it.copy(updateStatus = UpdateStatus.Downloading(progress, downloaded, total))
                }
            }

            when (downloadResult) {
                is AppResult.Success -> {
                    val filePath = downloadResult.data
                    _uiState.update {
                        it.copy(updateStatus = UpdateStatus.ReadyToInstall(filePath, release))
                    }
                    updateRepository.installApk(filePath)
                }

                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(updateStatus = UpdateStatus.Error("Tải bản cập nhật thất bại"))
                    }
                }
            }
        }
    }
}
