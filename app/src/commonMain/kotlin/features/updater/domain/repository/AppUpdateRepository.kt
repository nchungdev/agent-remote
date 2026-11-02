package app.duinch.agentremote.features.updater.domain.repository

import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.features.updater.domain.model.AppRelease

interface AppUpdateRepository {
    val currentVersionName: String
    val currentVersionCode: Int

    suspend fun checkUpdate(
        repoOwner: String = "nchungdev",
        repoName: String = "agent-remote"
    ): AppResult<AppRelease?, DomainError>

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): AppResult<String, DomainError>

    fun installApk(filePath: String): AppResult<Unit, DomainError>
}
