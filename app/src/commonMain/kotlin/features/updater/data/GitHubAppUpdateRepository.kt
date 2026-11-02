package app.duinch.agentremote.features.updater.data

import app.duinch.agentremote.core.domain.result.AppResult
import app.duinch.agentremote.core.domain.result.DomainError
import app.duinch.agentremote.features.updater.data.dto.GitHubReleaseDto
import app.duinch.agentremote.features.updater.domain.model.AppRelease
import app.duinch.agentremote.features.updater.domain.repository.AppUpdateRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

class GitHubAppUpdateRepository(
    private val httpClient: HttpClient = HttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val installer: PlatformApkInstaller = PlatformApkInstaller()
) : AppUpdateRepository {

    override val currentVersionName: String = "0.1.4"
    override val currentVersionCode: Int = 5

    override suspend fun checkUpdate(
        repoOwner: String,
        repoName: String
    ): AppResult<AppRelease?, DomainError> {
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=10"

        return try {
            val response = httpClient.get(apiUrl) {
                header("User-Agent", "AgentRemote-KMP")
                header("Accept", "application/vnd.github.v3+json")
            }

            if (response.status == HttpStatusCode.NotFound) {
                // No releases on GitHub yet
                return AppResult.Success(null)
            }

            if (response.status != HttpStatusCode.OK) {
                return AppResult.Failure(
                    DomainError.Network("GitHub API trả về mã lỗi: ${response.status.value}")
                )
            }

            val rawJson: String = response.body()
            val releases = json.decodeFromString<List<GitHubReleaseDto>>(rawJson)
            val dto = releases.firstOrNull { rel ->
                rel.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
            } ?: return AppResult.Success(null)

            val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val cleanTagName = dto.tagName.replace(Regex("^[^0-9]+"), "")
            val isNewer = compareVersions(cleanTagName, currentVersionName) > 0

            val release = AppRelease(
                tagName = dto.tagName,
                versionName = cleanTagName,
                versionCode = parseVersionCode(cleanTagName),
                releaseNotes = dto.body.orEmpty(),
                downloadUrl = apkAsset?.browserDownloadUrl,
                publishedAt = dto.publishedAt.orEmpty(),
                isNewerThanCurrent = isNewer
            )

            AppResult.Success(release)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.Network("Lỗi kiểm tra cập nhật: ${e.message}", e))
        }
    }

    override suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): AppResult<String, DomainError> {
        return try {
            val response = httpClient.get(downloadUrl) {
                header("User-Agent", "AgentRemote-KMP")
                onDownload { bytesSentTotal, contentLength ->
                    val total = contentLength ?: 0L
                    val progress = if (total > 0) bytesSentTotal.toFloat() / total.toFloat() else 0f
                    onProgress(progress, bytesSentTotal, total)
                }
            }

            val bytes = response.bodyAsChannel().readRemaining().readByteArray()
            val savedPath = installer.saveApkBytes(bytes)
            if (savedPath.isBlank()) {
                return AppResult.Failure(DomainError.Network("Không thể lưu tệp APK vào bộ nhớ đệm"))
            }
            AppResult.Success(savedPath)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.Network("Lỗi tải tệp APK: ${e.message}", e))
        }
    }

    override fun installApk(filePath: String): AppResult<Unit, DomainError> {
        val success = installer.installApk(filePath)
        return if (success) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(DomainError.Unknown("Không thể mở trình cài đặt gói APK"))
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
            2 -> parts[0] * 100 + parts[1]
            1 -> parts[0]
            else -> 1
        }
    }
}
