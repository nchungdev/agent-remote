package app.duinch.agentremote.features.updater.domain.model

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Available(val release: AppRelease) : UpdateStatus
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateStatus
    data class ReadyToInstall(val apkPath: String, val release: AppRelease) : UpdateStatus
    data class Error(val message: String) : UpdateStatus

    val isChecking: Boolean
        get() = this is Checking

    val isDownloading: Boolean
        get() = this is Downloading
}
