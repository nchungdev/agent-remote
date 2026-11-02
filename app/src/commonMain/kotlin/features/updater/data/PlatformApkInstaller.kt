package app.duinch.agentremote.features.updater.data

expect class PlatformApkInstaller() {
    fun installApk(filePath: String): Boolean
}
