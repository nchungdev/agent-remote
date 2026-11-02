package app.duinch.agentremote.features.updater.data

expect class PlatformApkInstaller() {
    fun saveApkBytes(bytes: ByteArray): String
    fun installApk(filePath: String): Boolean
}
