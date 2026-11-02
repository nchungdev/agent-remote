package app.duinch.agentremote.features.updater.data

expect class PlatformApkInstaller() {
    fun saveApkBytes(bytes: ByteArray, expectedSha256: String = ""): String
    fun installApk(filePath: String): Boolean
}
