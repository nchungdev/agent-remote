package app.duinch.agentremote.features.updater.domain.repository

import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.features.updater.domain.model.AppRelease

class FakeAppUpdateRepository(
    override val currentVersionName: String = "0.1.0",
    override val currentVersionCode: Int = 1,
    var mockRelease: AppRelease? = null,
    var shouldFail: Boolean = false
) : AppUpdateRepository {

    var installApkCalledWith: String? = null

    override suspend fun checkUpdate(
        repoOwner: String,
        repoName: String
    ): AppResult<AppRelease?, DomainError> {
        if (shouldFail) {
            return AppResult.Failure(DomainError.Network("Network error"))
        }
        return AppResult.Success(mockRelease)
    }

    override suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): AppResult<String, DomainError> {
        onProgress(1.0f, 1000L, 1000L)
        return AppResult.Success("fake_downloaded.apk")
    }

    override fun installApk(filePath: String): AppResult<Unit, DomainError> {
        installApkCalledWith = filePath
        return AppResult.Success(Unit)
    }
}
